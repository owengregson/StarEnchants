package bootstrap;

import compile.Compiler;
import compile.load.ContentHolder;
import compile.load.ItemsHolder;
import compile.load.ItemsLoader;
import compile.load.Library;
import compile.load.LibraryLoader;
import compile.load.MasterConfig;
import compile.load.MasterConfigHolder;
import compile.load.MasterConfigLoader;
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
import engine.stores.KeepOnDeathStore;
import engine.stores.KnockbackControlStore;
import engine.stores.RepeatStore;
import engine.stores.SoulModeStore;
import engine.stores.SuppressionStore;
import engine.stores.VarStore;
import engine.trigger.BuiltinTriggers;
import engine.trigger.TriggerRegistry;
import feature.apply.ItemEnchanter;
import feature.carrier.CarrierListener;
import feature.carrier.CarrierService;
import feature.combat.CombatDispatch;
import feature.combat.CombatListener;
import feature.combat.EquipListener;
import feature.combat.KeepOnDeathListener;
import feature.combat.KnockbackListener;
import feature.crystal.CrystalListener;
import feature.crystal.CrystalService;
import feature.heroic.HeroicListener;
import feature.heroic.HeroicService;
import feature.book.UnopenedBookListener;
import feature.book.UnopenedBookService;
import feature.menu.AdminBrowserMenu;
import feature.menu.AlchemistMenu;
import feature.menu.CrystalsBrowserMenu;
import feature.menu.EnchantMenu;
import feature.menu.EnchanterMenu;
import feature.menu.EnchantsBrowserMenu;
import feature.menu.GodlyTransmogMenu;
import feature.menu.MenuRegistry;
import feature.menu.ReferenceBrowserMenu;
import feature.menu.SetsBrowserMenu;
import feature.menu.TinkererMenu;
import feature.scroll.HolyScrollListener;
import feature.scroll.HolyScrollService;
import feature.scroll.NametagListener;
import feature.scroll.NametagService;
import feature.scroll.ScrollListener;
import feature.scroll.ScrollService;
import feature.slot.SlotListener;
import feature.slot.SlotService;
import feature.menu.MenuListener;
import feature.soul.SoulInteractListener;
import feature.soul.SoulInventoryListener;
import feature.soul.SoulListener;
import feature.soul.SoulService;
import feature.trigger.EngineStoreListener;
import feature.trigger.RepeatingDriver;
import feature.trigger.TriggerDispatch;
import feature.trigger.TriggerListeners;
import item.codec.CarrierCodec;
import item.codec.CombatCodec;
import item.codec.CrystalItemCodec;
import item.codec.HeroicUpgradeCodec;
import item.codec.ItemKeys;
import item.codec.ScrollCodec;
import item.codec.SlotItemCodec;
import item.codec.UnopenedBookCodec;
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
import platform.sched.TaskHandle;
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
    private RepeatingDriver passives; // §B REPEATING lifecycle — torn down in onDisable

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
        Path configFile = getDataFolder().toPath().resolve("config.yml");

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

        // Master config.yml (§L) — the sectioned cross-cutting knobs (slots/souls/crystals/heroic/lore/
        // integrations/reload). Another parallel immutable reference swapped in the same reload transaction;
        // services read its values through suppliers, so a reload re-tunes them live.
        MasterConfigHolder master = new MasterConfigHolder(MasterConfigLoader.load(configFile));
        logMaster(master.config());

        // Item read path: codec → ItemView cache → WornResolver → per-player WornStateStore.
        CombatCodec codec = new CombatCodec(ItemKeys.of(this).combat());
        ItemViewCache itemViews = new ItemViewCache(codec, initial.snapshot().generation());
        TriggerRegistry triggers = BuiltinTriggers.registry();
        WornResolver wornResolver = new WornResolver(itemViews, triggers.count(),
                triggers.attackTriggers(), triggers.defenseTriggers());
        WornStateStore worn = new WornStateStore(wornResolver::resolve);

        // Cold apply path: render lore from state (the display lookup reads the CURRENT library, so a
        // reload re-renders against new content) + the validating enchant/crystal apply service.
        LoreRenderer lore = new LoreRenderer(() -> loreStyle(master.config()),
                key -> content.library().displayNameOf(key));
        ItemEnchanter enchanter = new ItemEnchanter(codec, lore, content, ItemGroups.standard(),
                () -> master.config().slots().base(),          // §H base enchant slots
                () -> master.config().crystals().slots(),      // §E per-item crystal slots
                () -> master.config().crystals().maxStack());  // §E crystal sanity cap

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

        // Heroic upgrades (§F): mint + drag-apply onto armour/weapon (small success roll, material swap,
        // "heroic piece" lore rendered from state). Reuses the combat codec + lore renderer.
        HeroicUpgradeCodec heroicCodec = new HeroicUpgradeCodec(ItemKeys.of(this).heroicUpgrade());
        HeroicService heroics = new HeroicService(heroicCodec, codec, lore,
                () -> items.config().heroicOrDefault(), new java.util.Random());

        // Slot economy (§H): mint + drag-apply the upgrade orb (+N) / slot gem (+1) onto gear, raising its
        // persisted CombatState.added slot count (clamped to the config's universal hard cap). baseSlots
        // matches the ItemEnchanter default so the cap is computed against the same base capacity.
        SlotItemCodec slotItemCodec = new SlotItemCodec(ItemKeys.of(this).slotItem());
        SlotService slots = new SlotService(slotItemCodec, codec, lore,
                () -> items.config().slotsOrDefault(),
                (java.util.function.IntSupplier) () -> master.config().slots().base());

        // Book-economy scrolls (§I): black scroll extracts an enchant from gear into a book; randomizer
        // scroll rerolls a book's success. Reuse the combat codec + lore (gear) and CarrierService (mint
        // the extracted book / reroll book success). Distinct 'scroll' PDC tag, off the combat hot path.
        ScrollCodec scrollCodec = new ScrollCodec(ItemKeys.of(this).scroll());
        ScrollService scrolls = new ScrollService(scrollCodec, codec, lore, carriers, content,
                () -> items.config().scrollsOrDefault(), new java.util.Random());

        // Unopened/randomized book (§I): right-click yields a concrete enchant book of a random enchant
        // from its tier, at a random level + success. Mints through CarrierService's explicit-success book.
        UnopenedBookCodec unopenedCodec = new UnopenedBookCodec(ItemKeys.of(this).unopened());
        UnopenedBookService unopenedBooks = new UnopenedBookService(unopenedCodec, carriers, content,
                () -> items.config().unopenedBookOrDefault(), new java.util.Random());

        // Survival + cosmetic scrolls (§I): holy scroll keeps items on a death (PlayerDeathEvent scan);
        // item nametag renames gear via a chat-capture flow. Both share the 'scroll' PDC tag + scrolls config.
        HolyScrollService holyScrolls = new HolyScrollService(scrollCodec,
                () -> items.config().scrollsOrDefault(), new java.util.Random());
        NametagService nametags = new NametagService(scrollCodec, () -> items.config().scrollsOrDefault());

        // Souls: ONE ledger shared by the pipeline's gate 10 and the soul service, so a spend and a
        // gain-on-kill see the same in-memory authority.
        SoulLedger souls = new SoulLedger();
        SoulService soulService = new SoulService(souls, new SoulModeStore(),
                new SoulCodec(ItemKeys.of(this).soul()), () -> items.config().soulGemOrDefault(),
                () -> master.config().souls().depositOnAnyKill()); // §D deposit-on-any-kill master toggle

        // One shared writable variable store (§A): the SET_VAR/INVERT_VAR effects write it through the
        // per-event sink, and conditions read it back as %name% through the dispatchers' FactPopulator.
        VarStore vars = new VarStore();
        // One shared suppression store (§C SUPPRESS): the SUPPRESS effect writes it through the per-event
        // sink, and gate 5 of the pipeline reads it across the three DISABLE scopes. Same instance both ends.
        SuppressionStore suppression = new SuppressionStore();
        // One shared knockback-control store (§C KNOCKBACK_CONTROL): the effect writes a short-TTL per-victim
        // multiplier through the per-event sink, and the knockback listener (a SEPARATE event the same tick)
        // reads it to cancel/scale the launch. Same instance both ends.
        KnockbackControlStore knockback = new KnockbackControlStore();
        // One shared keep-on-death store (§C KEEP_ON_DEATH): the effect arms a short-TTL per-player flag
        // through the per-event sink; the death listener reads it on PlayerDeathEvent to keep items+levels.
        // Same instance both ends.
        KeepOnDeathStore keepOnDeath = new KeepOnDeathStore();

        // Protection / region gate (gate 2): compose the ProtectionProviders registered via the
        // ServicesManager; a server with none allows everything. The guard passes the firing location
        // (captured on the Activation, owned by the firing region) and the actor's UUID — no live-Player
        // lookup, so no cross-region read on Folia. A missing location is permissive (nothing to check).
        ProtectionService protection = new ProtectionService(
                master.config().integrations().protection()
                        ? ProtectionProviders.discover(getServer(), System.getLogger("StarEnchants.Protection"))
                        : List.of()); // §L config.yml integrations.protection: false → register against none
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
                new ActivationPipeline(new CooldownStore(), souls, suppression, protectionGuard, ActivationPipeline.Guard.ALLOW),
                areaScan(), this::fireActivation);
        // The effect-head → ParamSpec lookup the migrators use to write verbose v2 effects (ADR-0016).
        compile.SpecRegistry migrateSpecs = effects.specRegistry();
        // Economy bridge (gate-free): the MODIFY_MONEY effect deposits/withdraws/transfers through the sink,
        // routed to the global thread. Wraps the EconomyProvider registered via the ServicesManager;
        // absent ⇒ money effects are no-ops.
        EconomyService economy = master.config().integrations().economy()
                ? EconomyService.discover(getServer(), System.getLogger("StarEnchants.Economy"))
                : EconomyService.NONE; // §L config.yml integrations.economy: false → money effects are no-ops
        if (economy.present()) {
            getLogger().info("economy provider active");
        }
        CombatDispatch dispatch = new CombatDispatch(executor, handles, content, worn,
                triggers.idOf("ATTACK").orElseThrow(), triggers.idOf("DEFENSE").orElseThrow(),
                triggers.idOf("BOW").orElse(-1), triggers.idOf("TRIDENT").orElse(-1), tick::get,
                soulService::bindingFor, economy, soulService::debit, vars, suppression, knockback, keepOnDeath,
                () -> master.config().heroic().maxOutgoingFactor()); // §F heroic clamp ceiling
        // Non-combat triggers (MINE/KILL/FALL/FIRE/INTERACT*) — the events CombatDispatch does not cover.
        TriggerDispatch triggerDispatch = new TriggerDispatch(executor, handles, content, worn, triggers,
                tick::get, soulService::bindingFor, economy, soulService::debit, vars, suppression, knockback,
                keepOnDeath, () -> master.config().heroic().maxOutgoingFactor()); // §F heroic clamp ceiling
        // §B REPEATING lifecycle: one entity-owned repeating task per (player, repeating ability), armed by
        // EquipListener on every equip change and torn down on quit/disable. RepeatStore owns the mapping.
        passives = new RepeatingDriver(triggerDispatch, content, triggers.idOf("REPEATING").orElse(-1),
                new RepeatStore<TaskHandle>());

        getServer().getPluginManager().registerEvents(new CombatListener(dispatch), this);
        getServer().getPluginManager().registerEvents(new EquipListener(worn, content, passives), this);
        getServer().getPluginManager().registerEvents(new SoulListener(soulService), this);
        getServer().getPluginManager().registerEvents(new SoulInteractListener(soulService), this);
        getServer().getPluginManager().registerEvents(new SoulInventoryListener(soulService), this);
        getServer().getPluginManager().registerEvents(new TriggerListeners(triggerDispatch), this);
        getServer().getPluginManager().registerEvents(
                new EngineStoreListener(vars, suppression, knockback, keepOnDeath), this);
        // §C KEEP_ON_DEATH: keep items+levels on a death while the flag is armed. NORMAL priority — earlier
        // than HolyScrollListener (HIGH) — so an enchant-kept death never spends a holy scroll.
        getServer().getPluginManager().registerEvents(new KeepOnDeathListener(keepOnDeath, tick::get), this);
        // §C KNOCKBACK_CONTROL: hook whichever knockback event this server fires (modern bukkit / legacy
        // destroystokyo), capability-probed. A no-op on a server with neither (the effect is simply inert).
        KnockbackListener.Path knockbackPath = KnockbackListener.register(this, knockback, tick::get);
        getLogger().info("KNOCKBACK_CONTROL applier: " + knockbackPath);
        getServer().getPluginManager().registerEvents(new CarrierListener(carriers, carrierCodec), this);
        getServer().getPluginManager().registerEvents(new CrystalListener(crystals), this);
        getServer().getPluginManager().registerEvents(new HeroicListener(heroics), this);
        getServer().getPluginManager().registerEvents(new SlotListener(slots), this);
        getServer().getPluginManager().registerEvents(new ScrollListener(scrolls), this);
        getServer().getPluginManager().registerEvents(new UnopenedBookListener(unopenedBooks), this);
        getServer().getPluginManager().registerEvents(new HolyScrollListener(holyScrolls), this);
        getServer().getPluginManager().registerEvents(new NametagListener(nametags), this);
        // Heroic durability (§F): a heroic item's per-item durability chance cancels item-damage events.
        getServer().getPluginManager().registerEvents(
                new feature.heroic.HeroicDurabilityListener(codec, new java.util.Random()), this);

        // Arm REPEATING tasks for players already online (a plugin /reload with players on) — a fresh server
        // boot has none, so this is a no-op there; PlayerJoinEvent arms normal joins via EquipListener.
        for (Player player : getServer().getOnlinePlayers()) {
            Scheduling.onEntity(player, () -> passives.arm(player, worn.refresh(player, content.snapshot())));
        }

        // Reload: one persistent compiler; on a clean swap, advance the gen-keyed caches and re-resolve
        // every online player against the new snapshot (on each player's own thread).
        reloader = new ContentReloader(content, () -> compiler, contentRoot, 0, published -> {
            // Republish the parallel config references (items/ + master config.yml) in the SAME global-thread
            // reload step as the content swap — "one reload reloads everything" (§L). §L-4 tightens this into a
            // build-all-off-thread, all-or-nothing swap; here they re-parse on the global thread as items did.
            items.publish(ItemsLoader.load(itemsRoot));
            master.publish(MasterConfigLoader.load(configFile));
            itemViews.reload(published.snapshot().generation());
            getServer().getPluginManager().callEvent(new StarEnchantsReloadEvent(
                    published.snapshot().generation(), published.snapshot().abilityCount()));
            if (master.config().reload().reResolvePlayers()) { // §L config.yml reload.re-resolve-players
                for (Player player : getServer().getOnlinePlayers()) {
                    // Re-resolve worn state AND re-arm repeating tasks (their period may have changed) per player.
                    Scheduling.onEntity(player,
                            () -> passives.arm(player, worn.refresh(player, published.snapshot())));
                }
            }
        });

        // Optional auto-reload (§L config.yml reload.auto-seconds; ≤ 0 = off). Armed once at boot off the
        // initial config — changing the interval needs a restart. A recurring /se reload on the global thread.
        int autoSeconds = master.config().reload().autoSeconds();
        if (autoSeconds > 0) {
            long period = autoSeconds * 20L;
            Scheduling.repeatingGlobal(period, period, () -> reloader.reload(result -> { }));
            getLogger().info("auto-reload armed: every " + autoSeconds + "s");
        }

        // GUIs on the shared menu framework (§K). One listener routes every menu (recognised by MenuHolder);
        // the registry maps a name → menu so `/se menu <name>` opens any of them. The direct-apply enchant
        // menu ("apply") is the visual /se enchant. Menus open on the player's region thread (Folia open-hop).
        EnchantMenu applyMenu = new EnchantMenu(content, enchanter,
                player -> worn.refresh(player, content.snapshot()), caps);
        MenuRegistry menus = new MenuRegistry()
                .register(applyMenu)
                .register(new EnchantsBrowserMenu(content, caps))   // tier → enchant catalog browser
                .register(new SetsBrowserMenu(content, caps))       // armour-set browser + preview
                .register(new CrystalsBrowserMenu(content, caps))   // crystals/modifiers catalog
                .register(new ReferenceBrowserMenu(caps))           // effects/selectors/triggers/conditions/vars
                .register(new GodlyTransmogMenu(content, codec, scrolls, caps)) // reorder held gear's enchant lore
                .register(new EnchanterMenu(content, unopenedBooks, caps)) // buy mystery books per tier (XP)
                .register(new AlchemistMenu(carriers, caps))        // combine two identical books → +1 level
                .register(new TinkererMenu(carriers, caps))         // salvage an enchant book → XP refund
                .register(new AdminBrowserMenu(content, carriers, caps)); // admin grant browser (perm-gated)
        getServer().getPluginManager().registerEvents(new MenuListener(), this);

        PluginCommand command = getCommand("se");
        if (command != null) {
            SeCommand seCommand = new SeCommand(reloader, enchanter,
                    player -> worn.refresh(player, content.snapshot()), soulService,
                    getDataFolder().toPath().resolve("migrated"), menus, content,
                    head -> migrateSpecs.lookup(head).orElse(null), carriers, crystals, heroics, slots,
                    scrolls, unopenedBooks, holyScrolls, nametags);
            command.setExecutor(seCommand);
            command.setTabCompleter(seCommand); // subcommand + enchant/crystal-key completion
        }
    }

    @Override
    public void onDisable() {
        // Cancel every live REPEATING task so they don't leak across a plugin /reload (§B). The other
        // per-player stores self-bound via lazy TTL eviction; repeating tasks are the one thing that
        // outlives the JVM-less reload boundary and must be torn down explicitly.
        if (passives != null) {
            passives.disarmAll();
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

    /** The live {@code lore:} render style built from the master config (§L) — re-read on every render. */
    private static LoreStyle loreStyle(MasterConfig config) {
        MasterConfig.LoreSection l = config.lore();
        return new LoreStyle(l.enchantColor(), l.levelColor(), l.crystalColor(), l.roman(), l.unknownLabel());
    }

    /**
     * Extract the bundled defaults to the data folder on first boot: the {@code config.yml} master file plus
     * the {@code content/} and {@code items/} trees. Never overwrites an operator's edits.
     */
    private void saveDefaults() {
        saveDefaultFile("config.yml");
        saveDefaultTree("content");
        saveDefaultTree("items");
    }

    /** Extract one bundled top-level file (e.g. {@code config.yml}); never overwrites an operator's copy. */
    private void saveDefaultFile(String name) {
        if (Files.exists(getDataFolder().toPath().resolve(name))) {
            return;
        }
        try {
            saveResource(name, false);
        } catch (RuntimeException missing) {
            getLogger().warning("could not save default '" + name + "': " + missing.getMessage());
        }
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
                + ", heroic=" + config.heroic().isPresent()
                + ", slots=" + config.slots().isPresent()
                + ", scrolls=" + config.scrolls().isPresent()
                + ", unopened-book=" + config.unopenedBook().isPresent()
                + ", " + config.diagnostics().size() + " diagnostic(s), " + errors + " error(s)");
        for (Diagnostic diagnostic : config.diagnostics()) {
            getLogger().warning("  " + diagnostic);
        }
    }

    /** Log the master config.yml load result (the resolved cross-cutting knobs + any diagnostics). */
    private void logMaster(MasterConfig config) {
        getLogger().info("config.yml loaded: slots.base=" + config.slots().base()
                + ", crystals.slots=" + config.crystals().slots()
                + ", heroic.max-outgoing-factor=" + config.heroic().maxOutgoingFactor()
                + ", integrations[protection=" + config.integrations().protection()
                + ", economy=" + config.integrations().economy() + "]"
                + ", reload.auto-seconds=" + config.reload().autoSeconds()
                + ", " + config.diagnostics().size() + " diagnostic(s)");
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
