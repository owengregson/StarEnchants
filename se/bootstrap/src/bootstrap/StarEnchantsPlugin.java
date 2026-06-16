package bootstrap;

import compile.Compiler;
import compile.load.ContentHolder;
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
import feature.combat.CombatDispatch;
import feature.combat.CombatListener;
import feature.combat.EquipListener;
import feature.soul.SoulListener;
import feature.soul.SoulService;
import feature.trigger.TriggerDispatch;
import feature.trigger.TriggerListeners;
import item.codec.CombatCodec;
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

    /** Souls granted to the killer's active gem per kill (a v1 constant; config-driven later). */
    private static final int SOULS_PER_KILL = 1;

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

        saveDefaultContent();
        Path contentRoot = getDataFolder().toPath().resolve("content");

        // One retained resolver pairs compile-time interning with runtime resolution (§9).
        RegistryResolvers resolvers = new RegistryResolvers();
        Compiler compiler = ContentCompiler.production(resolvers);
        RuntimeHandles handles = new RuntimeHandles(resolvers);

        Library initial = loadInitial(compiler, contentRoot);
        content = new ContentHolder(initial);
        logLoad(initial);

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

        // Souls: ONE ledger shared by the pipeline's gate 10 and the soul service, so a spend and a
        // gain-on-kill see the same in-memory authority.
        SoulLedger souls = new SoulLedger();
        SoulService soulService = new SoulService(souls, new SoulModeStore(),
                new SoulCodec(ItemKeys.of(this).soul()));

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
        AbilityExecutor executor = new AbilityExecutor(BuiltinEffects.registry(), BuiltinSelectors.registry(),
                new ActivationPipeline(new CooldownStore(), souls, protectionGuard, ActivationPipeline.Guard.ALLOW),
                areaScan(), this::fireActivation);
        // Economy bridge (gate-free): GIVE_MONEY/TAKE_MONEY effects deposit/withdraw through the sink,
        // routed to the global thread. Wraps the EconomyProvider registered via the ServicesManager;
        // absent ⇒ money effects are no-ops.
        EconomyService economy = EconomyService.discover(getServer(), System.getLogger("StarEnchants.Economy"));
        if (economy.present()) {
            getLogger().info("economy provider active");
        }
        CombatDispatch dispatch = new CombatDispatch(executor, handles, content, worn,
                triggers.idOf("ATTACK").orElseThrow(), triggers.idOf("DEFENSE").orElseThrow(), tick::get,
                soulService::bindingFor, economy);
        // Non-combat triggers (MINE/KILL/FALL/FIRE/INTERACT*) — the events CombatDispatch does not cover.
        TriggerDispatch triggerDispatch = new TriggerDispatch(executor, handles, content, worn, triggers,
                tick::get, soulService::bindingFor, economy);

        getServer().getPluginManager().registerEvents(new CombatListener(dispatch), this);
        getServer().getPluginManager().registerEvents(new EquipListener(worn, content), this);
        getServer().getPluginManager().registerEvents(new SoulListener(soulService, SOULS_PER_KILL), this);
        getServer().getPluginManager().registerEvents(new TriggerListeners(triggerDispatch), this);

        // Reload: one persistent compiler; on a clean swap, advance the gen-keyed caches and re-resolve
        // every online player against the new snapshot (on each player's own thread).
        reloader = new ContentReloader(content, () -> compiler, contentRoot, 0, published -> {
            itemViews.reload(published.snapshot().generation());
            getServer().getPluginManager().callEvent(new StarEnchantsReloadEvent(
                    published.snapshot().generation(), published.snapshot().abilityCount()));
            for (Player player : getServer().getOnlinePlayers()) {
                Scheduling.onEntity(player, () -> worn.refresh(player, published.snapshot()));
            }
        });

        PluginCommand command = getCommand("se");
        if (command != null) {
            command.setExecutor(new SeCommand(reloader, enchanter,
                    player -> worn.refresh(player, content.snapshot()), soulService,
                    getDataFolder().toPath().resolve("migrated")));
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
            return new Library(compiler.compile(List.of(), 0, diagnostics),
                    List.of(), List.of(), List.of(), diagnostics.all());
        }
    }

    /** Extract the bundled content catalog to the data folder on first boot, driven by content/index.txt. */
    private void saveDefaultContent() {
        Path dataFolder = getDataFolder().toPath();
        for (String relative : shippedContentPaths()) {
            String resource = "content/" + relative;
            if (Files.exists(dataFolder.resolve(resource))) {
                continue; // never overwrite an operator's edited copy
            }
            try {
                saveResource(resource, false);
            } catch (RuntimeException missing) {
                getLogger().warning("could not save default content '" + resource + "': " + missing.getMessage());
            }
        }
    }

    /** The content paths to extract, read from the bundled {@code content/index.txt} manifest. */
    private List<String> shippedContentPaths() {
        InputStream in = getResource("content/index.txt");
        if (in == null) {
            getLogger().warning("content/index.txt missing from the jar — no default content extracted");
            return List.of();
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return reader.lines().map(String::trim).filter(line -> !line.isEmpty() && !line.startsWith("#")).toList();
        } catch (IOException e) {
            getLogger().warning("could not read content/index.txt: " + e.getMessage());
            return List.of();
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
