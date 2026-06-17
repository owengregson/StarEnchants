package bootstrap;

import compile.Compiler;
import compile.load.ContentHolder;
import compile.load.ItemsHolder;
import compile.load.ItemsLoader;
import compile.load.Library;
import compile.load.LibraryLoader;
import engine.boot.ContentCompiler;
import engine.effect.kind.BuiltinEffects;
import engine.interact.SoulLedger;
import engine.pipeline.ActivationPipeline;
import api.event.EnchantActivateEvent;
import api.event.StarEnchantsReloadEvent;
import compile.model.Ability;
import engine.run.AbilityExecutor;
import engine.run.ActivationContext;
import engine.run.AreaScan;
import engine.selector.kind.BuiltinSelectors;
import engine.stores.CooldownStore;
import engine.stores.SoulModeStore;
import engine.trigger.BuiltinTriggers;
import engine.trigger.TriggerRegistry;
import feature.apply.ItemEnchanter;
import feature.carrier.CarrierListener;
import feature.carrier.CarrierService;
import feature.combat.CombatDispatch;
import feature.combat.CombatListener;
import feature.combat.EquipListener;
import feature.crystal.CrystalListener;
import feature.crystal.CrystalService;
import feature.menu.EnchantMenu;
import feature.menu.MenuListener;
import feature.soul.SoulInteractListener;
import feature.soul.SoulListener;
import feature.soul.SoulService;
import feature.trigger.TriggerDispatch;
import feature.trigger.TriggerListeners;
import item.codec.CarrierCodec;
import item.codec.CombatCodec;
import item.codec.CrystalItemCodec;
import item.codec.ItemKeys;
import item.codec.SoulCodec;
import item.render.LoreRenderer;
import item.render.LoreStyle;
import item.view.ItemViewCache;
import item.worn.WornResolver;
import item.worn.WornStateStore;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import platform.caps.Capabilities;
import platform.content.ContentReloader;
import platform.economy.EconomyService;
import platform.item.ItemGroups;
import platform.protect.ProtectionProviders;
import platform.protect.ProtectionService;
import platform.resolve.RegistryResolvers;
import platform.resolve.RuntimeHandles;
import platform.sched.Scheduling;
import schema.diag.Diagnostic;
import schema.diag.Diagnostics;

/**
 * The StarEnchants plugin — the composition root (ADR-0014; §3). On enable it probes capabilities,
 * installs the {@code Scheduling} backend, loads {@code content/} into the published
 * {@link ContentHolder}, and wires the full combat spine: a per-player {@link WornStateStore} kept
 * fresh by the {@link EquipListener}, and a {@link CombatDispatch} (driven by the {@link CombatListener})
 * that runs worn abilities through the {@link AbilityExecutor} into the affinity-routed Sink and folds
 * damage onto the event. {@code /se reload} swaps content transactionally and re-resolves online players.
 *
 * <p>A single retained {@link RegistryResolvers} pairs the compiler (which interns handle tokens) with
 * the runtime {@link RuntimeHandles} (which resolves those ids back to objects) — the §9 round-trip;
 * reusing one compiler across reloads is safe because the reload is single-flight.
 */
public final class StarEnchantsPlugin extends JavaPlugin {

    private ContentHolder content;
    private ContentReloader reloader;

    @Override
    public void onEnable() {
        Capabilities caps = Capabilities.probe(getServer());
        Scheduling.init(this, caps);
        getLogger().info("StarEnchants — " + caps + ", scheduling "
                + Scheduling.backend().getClass().getSimpleName());

        // A monotonic game-tick counter (floor-safe) for cooldown timing.
        AtomicLong tick = new AtomicLong();
        Scheduling.repeatingGlobal(0L, 1L, tick::incrementAndGet);

        saveDefaults();
        Path contentRoot = getDataFolder().toPath().resolve("content");
        Path itemsRoot = getDataFolder().toPath().resolve("items");

        // One retained resolver pairs compile-time interning with runtime resolution (§9).
        RegistryResolvers resolvers = new RegistryResolvers();
        Compiler compiler = ContentCompiler.production(resolvers);
        RuntimeHandles handles = new RuntimeHandles(resolvers);

        Library initial = loadInitial(compiler, contentRoot);
        content = new ContentHolder(initial);
        logLoad(initial);

        // Item likeness/config (soul gem, …) — a parallel immutable snapshot of the top-level items/ folder,
        // published alongside content in the same /se reload transaction (the onPublished hook below).
        ItemsHolder items = new ItemsHolder(ItemsLoader.load(itemsRoot));
        logItems(items.config());

        // Item read path: codec → ItemView cache → WornResolver → per-player WornStateStore.
        CombatCodec codec = new CombatCodec(ItemKeys.of(this).combat());
        ItemViewCache itemViews = new ItemViewCache(codec, initial.snapshot().generation());
        TriggerRegistry triggers = BuiltinTriggers.registry();
        WornResolver wornResolver = new WornResolver(itemViews, triggers.count(),
                triggers.attackTriggers(), triggers.defenseTriggers());
        WornStateStore worn = new WornStateStore(wornResolver::resolve);

        // Cold apply path: render lore from state (the display lookup reads the CURRENT library, so a
        // reload re-renders against new content) + the validating enchant/crystal apply service.
        LoreRenderer lore = new LoreRenderer(LoreStyle.DEFAULT, key -> content.library().displayNameOf(key));
        ItemEnchanter enchanter = new ItemEnchanter(codec, lore, content, ItemGroups.standard());

        // Carrier economy (ADR-0016): mint + apply books/scrolls onto gear. Cold path — the carrier PDC is
        // separate from the combat blob, so it never decodes on the hot path. Random for the success roll.
        CarrierCodec carrierCodec = new CarrierCodec(ItemKeys.of(this).carrier(), ItemKeys.of(this).guarded());
        CarrierService carriers = new CarrierService(carrierCodec, enchanter, content, new java.util.Random());

        // Physical crystal items (§E): mint + drag-apply (success roll + consume) + multi-crystal merge.
        // The crystal-item PDC is separate from the combat blob; the applied crystal becomes one crystal-slot
        // entry on the gear (a multi-crystal is encoded "a+b" — one slot, both abilities).
        CrystalItemCodec crystalItemCodec = new CrystalItemCodec(ItemKeys.of(this).crystalItem());
        CrystalService crystals = new CrystalService(crystalItemCodec, enchanter, content,
                () -> items.config().crystalOrDefault(), new java.util.Random());

        // Souls: ONE ledger shared by the pipeline's gate 10 and the soul service, so a spend and a
        // gain-on-kill see the same in-memory authority.
        SoulLedger souls = new SoulLedger();
        SoulService soulService = new SoulService(souls, new SoulModeStore(),
                new SoulCodec(ItemKeys.of(this).soul()), () -> items.config().soulGemOrDefault());

        // Protection / region gate (gate 2): compose the ProtectionProviders registered via the
        // ServicesManager; a server with none allows everything. The guard passes the firing location
        // (captured on the Activation, owned by the firing region) and the actor's UUID — no live-Player
        // lookup, so no cross-region read on Folia. A missing location is permissive (nothing to check).
        ProtectionService protection = new ProtectionService(
                ProtectionProviders.discover(getServer(), System.getLogger("StarEnchants.Protection")));
        if (protection.providerCount() > 0) {
            getLogger().info("protection gate active with " + protection.providerCount() + " provider(s)");
        }
        ActivationPipeline.Guard protectionGuard = (ability, activation) -> {
            Location where = activation.location();
            return where == null || protection.allows(activation.actor(), where);
        };

        // Runtime executor + combat dispatch (the soul binder arms gate 10 from an actor's active gem).
        // The activation listener fires the public EnchantActivateEvent for each proc — Bukkit-aware
        // here, so the engine itself stays event-API-free (it only calls the callback).
        engine.effect.EffectRegistry effects = BuiltinEffects.registry();
        AbilityExecutor executor = new AbilityExecutor(effects, BuiltinSelectors.registry(),
                new ActivationPipeline(new CooldownStore(), souls, protectionGuard, ActivationPipeline.Guard.ALLOW),
                areaScan(), this::fireActivation);
        // The effect-head → ParamSpec lookup the migrators use to write verbose v2 effects (ADR-0016).
        compile.SpecRegistry migrateSpecs = effects.specRegistry();
        // Economy bridge (gate-free): GIVE_MONEY/TAKE_MONEY effects deposit/withdraw through the sink,
        // routed to the global thread. Wraps the EconomyProvider registered via the ServicesManager;
        // absent ⇒ money effects are no-ops.
        EconomyService economy = EconomyService.discover(getServer(), System.getLogger("StarEnchants.Economy"));
        if (economy.present()) {
            getLogger().info("economy provider active");
        }
        CombatDispatch dispatch = new CombatDispatch(executor, handles, content, worn,
                triggers.idOf("ATTACK").orElseThrow(), triggers.idOf("DEFENSE").orElseThrow(),
                triggers.idOf("BOW").orElse(-1), triggers.idOf("TRIDENT").orElse(-1), tick::get,
                soulService::bindingFor, economy);
        // Non-combat triggers (MINE/KILL/FALL/FIRE/INTERACT*) — the events CombatDispatch does not cover.
        TriggerDispatch triggerDispatch = new TriggerDispatch(executor, handles, content, worn, triggers,
                tick::get, soulService::bindingFor, economy);

        getServer().getPluginManager().registerEvents(new CombatListener(dispatch), this);
        getServer().getPluginManager().registerEvents(new EquipListener(worn, content), this);
        getServer().getPluginManager().registerEvents(new SoulListener(soulService), this);
        getServer().getPluginManager().registerEvents(new SoulInteractListener(soulService), this);
        getServer().getPluginManager().registerEvents(new TriggerListeners(triggerDispatch), this);
        getServer().getPluginManager().registerEvents(new CarrierListener(carriers, carrierCodec), this);
        getServer().getPluginManager().registerEvents(new CrystalListener(crystals), this);
        // Heroic durability (§F): a heroic item's per-item durability chance cancels item-damage events.
        getServer().getPluginManager().registerEvents(
                new feature.heroic.HeroicDurabilityListener(codec, new java.util.Random()), this);

        // Reload: one persistent compiler; on a clean swap, advance the gen-keyed caches and re-resolve
        // every online player against the new snapshot (on each player's own thread).
        reloader = new ContentReloader(content, () -> compiler, contentRoot, 0, published -> {
            // Republish the items/ config in the same global-thread reload step as the content swap.
            items.publish(ItemsLoader.load(itemsRoot));
            itemViews.reload(published.snapshot().generation());
            getServer().getPluginManager().callEvent(new StarEnchantsReloadEvent(
                    published.snapshot().generation(), published.snapshot().abilityCount()));
            for (Player player : getServer().getOnlinePlayers()) {
                Scheduling.onEntity(player, () -> worn.refresh(player, published.snapshot()));
            }
        });

        // Enchant-application GUI: clicking an enchant icon applies it to the held item (the visual /se
        // enchant). Opens on the player's thread; the click listener cancels item movement + applies inline.
        EnchantMenu menu = new EnchantMenu(content, enchanter,
                player -> worn.refresh(player, content.snapshot()));
        getServer().getPluginManager().registerEvents(new MenuListener(menu), this);

        PluginCommand command = getCommand("se");
        if (command != null) {
            SeCommand seCommand = new SeCommand(reloader, enchanter,
                    player -> worn.refresh(player, content.snapshot()), soulService,
                    getDataFolder().toPath().resolve("migrated"), menu, content,
                    head -> migrateSpecs.lookup(head).orElse(null), carriers, crystals);
            command.setExecutor(seCommand);
            command.setTabCompleter(seCommand); // subcommand + enchant/crystal-key completion
        }
    }

    /** The published content the runtime reads. */
    public ContentHolder content() {
        return content;
    }

    /** An area scan over the firing region — used by AOE/NEAREST selectors (§3.6). */
    private static AreaScan areaScan() {
        return (center, radius) -> {
            World world = center.getWorld();
            List<LivingEntity> out = new ArrayList<>();
            if (world != null) {
                for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
                    if (entity instanceof LivingEntity living) {
                        out.add(living);
                    }
                }
            }
            return out;
        };
    }

    /**
     * The {@code ActivationListener} the executor calls per proc — fires the public
     * {@link EnchantActivateEvent} naming the activated ability's stable key. Runs on the firing
     * thread (the player's region on Folia), which is the correct thread to dispatch the event from.
     *
     * <p>The {@code key} is resolved by the executor against the SAME snapshot whose abilities fired
     * (not re-read here from the live holder, which a concurrent {@code /se reload} could have swapped
     * — that would mis-name or drop the event). A {@code null} key is the defensive "couldn't resolve"
     * case: skip rather than fire an unattributable event.
     */
    private void fireActivation(String key, Ability ability, ActivationContext context) {
        Player actor = context.actor();
        if (actor == null || key == null) {
            return; // synthetic/non-player activation, or an unresolvable key — nothing to fire
        }
        getServer().getPluginManager().callEvent(new EnchantActivateEvent(actor, key, ability.level()));
    }

    /** The initial load, guaranteed not to throw out of onEnable — a content I/O fault boots empty. */
    private Library loadInitial(Compiler compiler, Path contentRoot) {
        try {
            return LibraryLoader.load(contentRoot, compiler, 0);
        } catch (Throwable failure) {
            getLogger().severe("content load failed; enabling with no content: " + failure);
            Diagnostics diagnostics = new Diagnostics();
            return Library.empty(compiler.compile(List.of(), 0, diagnostics), diagnostics.all());
        }
    }

    /** Extract the bundled default trees to the data folder on first boot ({@code content/} + {@code items/}). */
    private void saveDefaults() {
        saveDefaultTree("content");
        saveDefaultTree("items");
    }

    /** Extract one bundled tree, driven by {@code <root>/index.txt}; never overwrites an operator's edits. */
    private void saveDefaultTree(String root) {
        Path dataFolder = getDataFolder().toPath();
        for (String relative : shippedPaths(root)) {
            String resource = root + "/" + relative;
            if (Files.exists(dataFolder.resolve(resource))) {
                continue; // never overwrite an operator's edited copy
            }
            try {
                saveResource(resource, false);
            } catch (RuntimeException missing) {
                getLogger().warning("could not save default '" + resource + "': " + missing.getMessage());
            }
        }
    }

    /** The paths to extract for {@code root}, read from the bundled {@code <root>/index.txt} manifest. */
    private List<String> shippedPaths(String root) {
        InputStream in = getResource(root + "/index.txt");
        if (in == null) {
            getLogger().warning(root + "/index.txt missing from the jar — no defaults extracted for " + root);
            return List.of();
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return reader.lines().map(String::trim).filter(line -> !line.isEmpty() && !line.startsWith("#")).toList();
        } catch (IOException e) {
            getLogger().warning("could not read " + root + "/index.txt: " + e.getMessage());
            return List.of();
        }
    }

    /** Log the items/ config load result (count of configured items + any diagnostics). */
    private void logItems(compile.load.ItemsConfig config) {
        long errors = config.diagnostics().stream().filter(Diagnostic::blocking).count();
        getLogger().info("items config loaded: soul-gem=" + config.soulGem().isPresent()
                + ", crystal=" + config.crystal().isPresent()
                + ", " + config.diagnostics().size() + " diagnostic(s), " + errors + " error(s)");
        for (Diagnostic diagnostic : config.diagnostics()) {
            getLogger().warning("  " + diagnostic);
        }
    }

    private void logLoad(Library library) {
        long errors = library.diagnostics().stream().filter(Diagnostic::blocking).count();
        getLogger().info("content loaded: " + library.snapshot().abilityCount() + " abilities, "
                + library.diagnostics().size() + " diagnostic(s), " + errors + " error(s)");
        for (Diagnostic diagnostic : library.diagnostics()) {
            getLogger().warning("  " + diagnostic);
        }
    }
}
