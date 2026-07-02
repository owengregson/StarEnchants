package bootstrap;

import compile.Compiler;
import compile.load.ContentHolder;
import compile.load.ItemsHolder;
import compile.load.ItemsLoader;
import compile.load.LangHolder;
import compile.load.LangLoader;
import compile.load.Library;
import compile.load.LibraryLoader;
import compile.load.MasterConfig;
import compile.load.MasterConfigHolder;
import compile.load.MasterConfigLoader;
import compile.load.MenusHolder;
import compile.load.MenusLoader;
import engine.boot.ContentCompiler;
import engine.effect.kind.BuiltinEffects;
import engine.interact.SoulPool;
import engine.pipeline.ActivationPipeline;
import api.event.EnchantActivateEvent;
import api.event.StarEnchantsReloadEvent;
import compile.model.Ability;
import compile.model.Snapshot;
import engine.run.AbilityExecutor;
import engine.run.AbilityQuarantine;
import engine.run.ActivationContext;
import engine.run.AreaScan;
import engine.selector.kind.BuiltinSelectors;
import engine.stores.ComboStore;
import engine.stores.CooldownStore;
import engine.stores.ImmuneStore;
import engine.stores.KeepOnDeathStore;
import engine.stores.KnockbackControlStore;
import engine.stores.TeleblockStore;
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
import feature.combat.ImmuneListener;
import feature.combat.KeepOnDeathListener;
import feature.combat.KnockbackListener;
import feature.combat.MentalKnockbackBridge;
import feature.combat.TeleblockListener;
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
import feature.menu.MintCatalog;
import feature.menu.MintMenu;
import feature.menu.OperatorConsoleMenu;
import feature.menu.ReferenceBrowserMenu;
import feature.menu.SetsBrowserMenu;
import feature.menu.TinkererMenu;
import feature.menu.UserHubMenu;
import feature.menu.UserMenuCommand;
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
import feature.trigger.CommandTriggerCommand;
import feature.trigger.LifecycleDriver;
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
import item.lang.Messages;
import item.render.LoreRenderer;
import item.render.LoreStyle;
import item.view.ItemViewCache;
import item.worn.WornResolver;
import item.worn.WornStateStore;
import pack.PackStore;
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
import integrate.Integrations;
import platform.economy.EconomyProvider;
import platform.economy.EconomyService;
import platform.item.ItemGroups;
import platform.protect.ProtectionProvider;
import platform.protect.ProtectionProviders;
import platform.protect.ProtectionService;
import platform.resolve.RegistryResolvers;
import platform.sched.Scheduling;
import platform.sched.TaskHandle;
import schema.diag.Diagnostic;
import schema.diag.Diagnostics;

/**
 * The composition root (ADR-0014; §3): probe → install scheduling → load content → wire the combat
 * spine and feature listeners. One retained {@link RegistryResolvers} pairs compile-time interning with
 * the runtime resolver (§9; the modern/legacy split lives behind the {@code bootstrap.compat.Wiring} seam);
 * reusing one compiler across reloads is safe — reload is single-flight.
 */
public final class StarEnchantsPlugin extends JavaPlugin {

    private ContentHolder content;
    private ContentReloader reloader;
    private RepeatingDriver passives;     // §B REPEATING lifecycle
    private LifecycleDriver lifecycle;    // §B HELD/PASSIVE lifecycle
    private feature.trigger.PassiveEffectDriver passiveEffects; // §B maintained passive POTION buffs (permanent + suppression-aware)

    /** §B passive-potion maintenance sweep period (ticks): the safety-net re-derive cadence; instant paths handle the rest. */
    private static final long PASSIVE_SWEEP_TICKS = 40L;
    private feature.soul.SoulParticleDriver soulParticles; // §D while-active soul aura
    private bstats.Metrics metrics;       // bStats id 32197

    @Override
    public void onEnable() {
        Capabilities caps = Capabilities.probe(getServer());
        Scheduling.init(this, caps);
        getLogger().info("StarEnchants — " + caps + ", scheduling "
                + Scheduling.backend().getClass().getSimpleName());

        // bStats (id 32197): the vendored Metrics is Folia-aware. Owners opt out via plugins/bStats/config.yml.
        metrics = new bstats.Metrics(this, 32197);

        // Monotonic game-tick counter for cooldown timing.
        AtomicLong tick = new AtomicLong();
        Scheduling.repeatingGlobal(0L, 1L, tick::incrementAndGet);

        saveDefaults();
        Path contentRoot = getDataFolder().toPath().resolve("content");
        Path itemsRoot = getDataFolder().toPath().resolve("items");
        Path configFile = getDataFolder().toPath().resolve("config.yml");
        Path langFile = getDataFolder().toPath().resolve("lang.yml");
        Path menusRoot = getDataFolder().toPath().resolve("menus");

        RegistryResolvers resolvers = new RegistryResolvers();
        Compiler compiler = ContentCompiler.production(resolvers);
        // Runtime resolver wiring (modern RuntimeHandles vs legacy NMS-by-name) lives behind the overlay seam.
        bootstrap.compat.Wiring wiring = new bootstrap.compat.Wiring(resolvers);
        feature.fx.ParticleFx particleFx = wiring.particleFx();

        Library initial = loadInitial(compiler, contentRoot);
        content = new ContentHolder(initial);
        logLoad(initial);

        // The §L parallel config sources (items/, config.yml, lang.yml, menus/) — each an immutable
        // reference swapped in the same reload transaction (see reloadSteps), read live through suppliers
        // so a reload re-tunes services without a restart.
        ItemsHolder items = new ItemsHolder(ItemsLoader.load(itemsRoot));
        logItems(items.config());

        MasterConfigHolder master = new MasterConfigHolder(MasterConfigLoader.load(configFile));
        logMaster(master.config());

        LangHolder lang = new LangHolder(LangLoader.load(langFile));
        Messages messages = new Messages(lang::lang,
                () -> master.config().messages().prefix(),
                () -> master.config().messages().feedback(),
                // §N PlaceholderAPI passthrough (ADR-0027): resolve other plugins' %…% when present, else identity.
                Integrations.placeholderResolver(this, master.config().integrations()::enabled));

        MenusHolder menusHolder = new MenusHolder(MenusLoader.load(menusRoot));

        // Item read path: codec → ItemView cache → WornResolver → per-player WornStateStore.
        CombatCodec codec = new CombatCodec(ItemKeys.of().combat());
        ItemViewCache itemViews = new ItemViewCache(codec, initial.snapshot().generation());
        TriggerRegistry triggers = BuiltinTriggers.registry();
        WornResolver wornResolver = new WornResolver(itemViews, triggers.count(),
                triggers.attackTriggers(), triggers.defenseTriggers(),
                () -> {                                            // §L per-feature master toggles (live)
                    MasterConfig.FeaturesSection ff = master.config().features();
                    return new WornResolver.Features(ff.enchants(), ff.sets(), ff.crystals(), ff.heroic());
                },
                () -> {                                            // §ADR-0035 non-stackable crystal keys (live)
                    java.util.Set<String> keys = new java.util.HashSet<>();
                    for (compile.load.CrystalDef def : content.library().crystals()) {
                        if (!def.stackable()) {
                            keys.add(def.key());
                        }
                    }
                    return keys;
                });
        WornStateStore worn = new WornStateStore(wornResolver::resolve);

        // §I the applied-utility marker set, shared by white/holy scrolls and the trak gems (independent markers).
        // Built before the renderer because the lore's PROTECTED-line reader closes over both of these.
        item.codec.AppliedSlot appliedSlot = new item.codec.AppliedSlot(ItemKeys.of().appliedSlot());
        // Carrier economy (ADR-0016). Carrier PDC is separate from the combat blob, so it never decodes hot.
        CarrierCodec carrierCodec = new CarrierCodec(ItemKeys.of().carrier(), ItemKeys.of().guarded());

        // §I Robust lore composition: identify trak count lines (by the format's visible prefix) and PROTECTED
        // lines (by the templates' visible text) so each lore writer PRESERVES the others' lines. This is what
        // lets a soul gem keep its authored lore, and traks survive, when a scroll is applied (and vice versa).
        java.util.function.Predicate<String> trakLineP = line ->
                feature.trak.TrakService.isCountLine(line, items.config().traksOrDefault());
        java.util.function.Predicate<String> protectionLineP = line -> item.render.ProtectionLore.isProtectionLine(line,
                items.config().whiteScrollOrDefault().protectedLine(),
                items.config().scrollsOrDefault().holy().protectedLine());
        java.util.function.Function<org.bukkit.inventory.ItemStack, java.util.List<String>> protectionLinesFn = stack ->
                item.render.ProtectionLore.lines(carrierCodec.isGuarded(stack),
                        appliedSlot.holds(stack, item.codec.AppliedSlot.HOLY),
                        items.config().whiteScrollOrDefault().protectedLine(),
                        items.config().scrollsOrDefault().holy().protectedLine());
        // Scroll appliers re-stamp ONLY the PROTECTED line(s), preserving the body / authored economy lore + traks.
        java.util.function.Consumer<org.bukkit.inventory.ItemStack> protectionRefresh = gear ->
                item.render.ProtectionLoreRefresh.refresh(gear, protectionLinesFn.apply(gear), protectionLineP, trakLineP);

        // Cold apply path. Lookups read the CURRENT library, so a reload re-renders against new content.
        LoreRenderer lore = new LoreRenderer(() -> loreStyle(master.config()),
                key -> content.library().displayNameOf(key),
                key -> {                            // per-enchant rarity-tier colour (ADR-0016 §2); null → universal
                    String tier = content.library().tierOf(key);
                    if (tier == null) {
                        return null;
                    }
                    compile.load.TierRegistry.Tier t = content.library().tiers().tier(tier);
                    return t != null && !t.color().isBlank() ? t.color() : null;
                },
                new LoreRenderer.SetLore() {        // §6.6 set-member lore, read live from the current library
                    @Override public java.util.List<String> armor(String setKey) {
                        compile.load.SetDef def = content.library().setDefOf(setKey);
                        return def != null ? def.armorLore() : java.util.List.of();
                    }

                    @Override public java.util.List<String> weapon(String setKey) {
                        compile.load.SetDef def = content.library().setDefOf(setKey);
                        return def != null ? def.weaponLore() : java.util.List.of();
                    }
                },
                protectionLinesFn, // §I applied-scroll PROTECTED lines, from marker state
                trakLineP,         // §I preserve applied-trak count lines across a body re-render
                () -> items.config().scrollsOrDefault().transmog().nameSuffix(), // §I enchant-count name suffix
                () -> master.config().slots().base(),       // §H base slots → the orb "Enchantment Slots" total
                () -> master.config().slots().loreLine(),   // §H orb "Enchantment Slots" line template
                () -> items.config().heroicOrDefault().loreLine(), // §F HEROIC line template
                () -> items.config().crystalOrDefault().loreWhileOnItem(),      // §E on-gear crystal line template
                () -> items.config().crystalOrDefault().loreWhileOnItemMulti()); // §E merged-crystal on-gear line (ADR-0035)
        ItemGroups itemGroups = ItemGroups.standard();                 // §I shared by the enchanter + trak gems
        ItemEnchanter enchanter = new ItemEnchanter(codec, lore, content, itemGroups,
                () -> master.config().slots().base(),          // §H base enchant slots
                () -> master.config().crystals().slots(),      // §E per-item crystal slots (entries)
                () -> master.config().crystals().maxMerge(),   // §E components per entry (merge cap)
                messages);                                     // §L ApplyResult reason strings

        // Carrier economy (ADR-0016) — carrierCodec/appliedSlot built above (the lore PROTECTED-line reader uses them).
        CarrierService carriers = new CarrierService(carrierCodec, enchanter, content, new java.util.Random(),
                () -> items.config().enchantBookOrDefault(),   // §I enchant book
                () -> items.config().dustOrDefault(),          // §I success dust
                () -> items.config().whiteScrollOrDefault(),   // §I white scroll
                () -> master.config().lore().roman(),          // book level numeral style (lore.roman, live)
                () -> master.config().books().maxSuccess(),    // §I global success ceiling (books.max-success, live)
                appliedSlot,                                   // §I white scroll occupies this
                protectionRefresh,                             // §I toggle PROTECTED without wiping the rest of the lore
                itemGroups,                                    // §I white-scroll applies-to gate
                messages);                                     // §I applies reject reads common.wrong-applies

        // Physical crystal items (§E). A multi-crystal is one crystal-slot entry encoding "a+b".
        CrystalItemCodec crystalItemCodec = new CrystalItemCodec(ItemKeys.of().crystalItem());
        item.codec.CrystalExtractorCodec crystalExtractorCodec =
                new item.codec.CrystalExtractorCodec(ItemKeys.of().crystalExtractor());
        CrystalService crystals = new CrystalService(crystalItemCodec, crystalExtractorCodec, enchanter, content,
                () -> items.config().crystalOrDefault(), () -> master.config().crystals().maxMerge(), messages);

        // Heroic upgrades (§F).
        HeroicUpgradeCodec heroicCodec = new HeroicUpgradeCodec(ItemKeys.of().heroicUpgrade());
        HeroicService heroics = new HeroicService(heroicCodec, codec, lore,
                () -> items.config().heroicOrDefault(), new java.util.Random(), messages);

        // Slot economy (§H). base MUST match the ItemEnchanter default so the cap is computed off the same base.
        SlotItemCodec slotItemCodec = new SlotItemCodec(ItemKeys.of().slotItem(), ItemKeys.of().slotSuccess());
        SlotService slots = new SlotService(slotItemCodec, codec, lore,
                () -> items.config().slotsOrDefault(),
                (java.util.function.IntSupplier) () -> master.config().slots().base(), messages, itemGroups);

        // Book-economy scrolls (§I). Distinct 'scroll' PDC tag, off the combat hot path.
        ScrollCodec scrollCodec = new ScrollCodec(ItemKeys.of().scroll(), ItemKeys.of().scrollConvert());
        item.codec.GodlyTransmogCodec godlyTransmogCodec =
                new item.codec.GodlyTransmogCodec(ItemKeys.of().godlyTransmog());
        ScrollService scrolls = new ScrollService(scrollCodec, codec, lore, carriers, content,
                () -> items.config().scrollsOrDefault(), new java.util.Random(), messages, godlyTransmogCodec, itemGroups);

        // Unopened/randomized book (§I).
        UnopenedBookCodec unopenedCodec = new UnopenedBookCodec(ItemKeys.of().unopened());
        UnopenedBookService unopenedBooks = new UnopenedBookService(unopenedCodec, carriers, content,
                () -> items.config().unopenedBookOrDefault(), new java.util.Random(), messages);

        // Survival + cosmetic scrolls (§I) — both share the 'scroll' PDC tag + scrolls config.
        HolyScrollService holyScrolls = new HolyScrollService(scrollCodec, appliedSlot,
                () -> items.config().scrollsOrDefault(), new java.util.Random(), messages, protectionRefresh, itemGroups);
        feature.scroll.KeptItemsStore keptItems = new feature.scroll.KeptItemsStore(); // §I holy death→respawn stash
        NametagService nametags = new NametagService(scrollCodec, () -> items.config().scrollsOrDefault(),
                messages, codec); // §I codec → re-append the enchant-count suffix on rename + preview

        // Trak gems (§I): block/mob/soul lifetime counters tracked in the background on eligible gear.
        item.codec.TrakCodec trakCodec = new item.codec.TrakCodec(ItemKeys.of().trakGem(),
                ItemKeys.of().trakBlocks(), ItemKeys.of().trakMobs(), ItemKeys.of().trakSouls(),
                ItemKeys.of().trakFish());
        feature.trak.TrakService traks = new feature.trak.TrakService(trakCodec, appliedSlot, itemGroups,
                () -> items.config().traksOrDefault(), messages);

        // Souls (§D): the per-player cross-gem SoulPool is the spend authority. The SoulService owns it and is
        // ALSO the pipeline's gate-10 SoulSpender, so a gate-10 spend and the holder-thread drain share one pool.
        SoulPool soulPool = new SoulPool();
        SoulModeStore soulModes = new SoulModeStore(); // shared by the service + the §D while-active aura driver
        SoulService soulService = new SoulService(soulPool, soulModes,
                new SoulCodec(ItemKeys.of().soul()), () -> items.config().soulGemOrDefault(),
                () -> master.config().souls().depositOnAnyKill(), messages, particleFx, // §D deposit + §L msgs + particles
                line -> protectionLineP.test(line) || trakLineP.test(line)); // §I keep scroll/trak lines on gem re-render
        // §N PlaceholderAPI expansion (ADR-0027). Accessors are plain JDK-typed, so PAPI never loads internals.
        Integrations.registerPlaceholders(this, master.config().integrations()::enabled,
                player -> soulModes.isActive(player.getUniqueId()),
                // §D total souls across ALL carried gems (cached on the holder thread each tick — thread-safe here)
                player -> soulService.soulTotal(player.getUniqueId()));
        // §D soul-mode tick: one global task that auto-disables soul mode when a player's active gem is gone or
        // drained to zero, then spawns the configured while-active aura at players still in soul mode.
        soulParticles = new feature.soul.SoulParticleDriver(
                soulService, soulModes, () -> items.config().soulGemOrDefault(), particleFx);
        soulParticles.start();

        // Each store below is ONE shared instance: an effect writes it through the per-event sink and a
        // separate reader (a pipeline gate, or a different Bukkit event the same tick) reads it back.
        VarStore vars = new VarStore();                  // §A SET_VAR/INVERT_VAR → conditions' %name%
        SuppressionStore suppression = new SuppressionStore(); // §C SUPPRESS → gate 5 across DISABLE scopes
        KnockbackControlStore knockback = new KnockbackControlStore(); // §C KNOCKBACK_CONTROL → knockback listener
        KeepOnDeathStore keepOnDeath = new KeepOnDeathStore();        // §C KEEP_ON_DEATH → PlayerDeathEvent
        // TELEBLOCK / IMMUNE (Cosmic Enchants exotic-effect ports): per-player timed flag → teleport / damage listeners.
        TeleblockStore teleblock = new TeleblockStore();
        ImmuneStore immune = new ImmuneStore();

        // Protection / region gate (gate 2): bundled providers (§N, ADR-0027) + ServicesManager; none ⇒ allow.
        // The guard takes the Activation's captured location + actor UUID — no live-Player cross-region read.
        List<ProtectionProvider> protectionProviders = new ArrayList<>();
        if (master.config().integrations().protection()) {
            protectionProviders.addAll(
                    Integrations.protectionProviders(this, master.config().integrations()::enabled));
            protectionProviders.addAll(
                    ProtectionProviders.discover(getServer(), System.getLogger("StarEnchants.Protection")));
        } // §L config.yml integrations.protection: false → gate against none
        ProtectionService protection = new ProtectionService(protectionProviders);
        if (protection.providerCount() > 0) {
            getLogger().info("protection gate active with " + protection.providerCount() + " provider(s)");
        }
        ActivationPipeline.Guard protectionGuard = (ability, activation) -> {
            Location where = activation.location();
            return where == null || protection.allows(activation.actor(), where);
        };

        // fireActivation fires EnchantActivateEvent per proc — Bukkit-aware here, so the engine stays event-API-free.
        engine.effect.EffectRegistry effects = BuiltinEffects.registry();
        // §L global message-on-activate: the holder ("BY you") + the other party ("ON you") get a configured line.
        feature.combat.ActivationMessenger activationMessenger = new feature.combat.ActivationMessenger(
                () -> master.config().messageOnActivate(), content);
        // Named + registered with EngineStoreListener like its sibling stores, not inline in the pipeline, so the
        // one quit-cleanup authority frees a leaver's cooldown entries (the TTL is the only other bound).
        CooldownStore cooldowns = new CooldownStore();
        // Combat-local streak store, hoisted here (not private in CombatDispatch) so it too is quit-cleaned there.
        ComboStore combo = new ComboStore();
        AbilityExecutor executor = new AbilityExecutor(effects, BuiltinSelectors.registry(),
                new ActivationPipeline(cooldowns, soulService, suppression, protectionGuard, ActivationPipeline.Guard.ALLOW),
                areaScan(), (key, ability, context) -> {
                    if (key == null) {
                        return; // a null key is skipped, not faked
                    }
                    fireActivation(key, ability, context);                 // EnchantActivateEvent (public API)
                    activationMessenger.onActivate(key, ability, context); // §L the configured BY/ON lines
                });
        // §10 runtime quarantine, bound to the live snapshot and rebound per reload (see the reloader callback).
        executor.bindQuarantine(quarantineFor(content.snapshot()));
        // The effect-head → ParamSpec lookup the migrators use to write verbose v2 effects (ADR-0016).
        compile.SpecRegistry migrateSpecs = effects.specRegistry();
        // Economy bridge for MODIFY_MONEY (global thread): bundled Vault (§N, ADR-0027) → ServicesManager → no-ops.
        EconomyService economy;
        if (master.config().integrations().economy()) {
            EconomyProvider bundled = Integrations.economyProvider(this, master.config().integrations()::enabled);
            economy = bundled != null
                    ? new EconomyService(bundled)
                    : EconomyService.discover(getServer(), System.getLogger("StarEnchants.Economy"));
        } else {
            economy = EconomyService.NONE; // §L config.yml integrations.economy: false → money effects are no-ops
        }
        if (economy.present()) {
            getLogger().info("economy provider active");
        }
        // §N soft integration hooks (ADR-0027), set once at boot, no-op when the target is absent — each a
        // reflective seam so the engine keeps no hard dep on these plugins:
        // anti-cheat movement exemption for engine-applied VELOCITY/TELEPORT,
        engine.sink.DispatchSink.movementExemption(Integrations.antiCheatExemption(
                this, master.config().integrations()::enabled, System.getLogger("StarEnchants.AntiCheat")));
        // mcMMO friendly-fire gate,
        CombatDispatch.friendlyFire(Integrations.mcmmoFriendlyFire(this, master.config().integrations()::enabled));
        // %victim.mobtype% from MythicMobs' internal name,
        engine.run.FactPopulator.entityTypeResolver(
                Integrations.mythicMobType(this, master.config().integrations()::enabled));
        // itemsadder:… / oraxen:… custom-item materials in item/menu configs.
        item.mint.ItemFactory.customItemResolver(
                Integrations.customItem(this, master.config().integrations()::enabled));
        // §L universal economy-item lore wrap width (lore.item-wrap), read live so a /se reload re-tunes it.
        item.mint.ItemFactory.itemWrapWidth(() -> master.config().lore().itemWrap());
        // §6.6 set-piece base enchants (Protection/Unbreaking/Sharpness) resolve cross-version behind the seam.
        item.mint.ItemFactory.enchantResolver(wiring.enchantResolver());
        CombatDispatch dispatch = new CombatDispatch(executor, wiring.sinkFactory(), content, worn,
                triggers.idOf("ATTACK").orElseThrow(), triggers.idOf("DEFENSE").orElseThrow(),
                triggers.idOf("BOW").orElse(-1), triggers.idOf("TRIDENT").orElse(-1), tick::get,
                soulService::bindingFor, economy, soulService, vars, suppression, knockback, keepOnDeath,
                teleblock, immune, combo,
                () -> master.config().heroic().maxOutgoingFactor(),       // §F heroic clamp ceiling
                () -> master.config().combat().maxBonusDamage(),          // §L combat.max-bonus-damage (live)
                () -> master.config().combat().maxBonusReduction(),       // §L combat.max-bonus-reduction (live)
                () -> master.config().combat().pvp(),                     // §L combat.pvp gate (live)
                () -> master.config().combat().pve());                    // §L combat.pve gate (live)
        // Non-combat triggers (MINE/KILL/FALL/FIRE/INTERACT*) — the events CombatDispatch does not cover.
        TriggerDispatch triggerDispatch = new TriggerDispatch(executor, wiring.sinkFactory(), content, worn, triggers,
                tick::get, soulService::bindingFor, economy, soulService, vars, suppression, knockback,
                keepOnDeath, teleblock, immune,
                () -> master.config().heroic().maxOutgoingFactor()); // §F heroic clamp ceiling
        // §B REPEATING: one entity-owned repeating task per (player, ability), armed/torn-down by EquipListener.
        passives = new RepeatingDriver(triggerDispatch, content, triggers.idOf("REPEATING").orElse(-1),
                new RepeatStore<TaskHandle>());
        // §B HELD/PASSIVE buffs that flip on/off at equip/unequip via EquipListener's worn-ability diff (ADR-0022).
        lifecycle = new LifecycleDriver(triggerDispatch, content,
                triggers.idOf("HELD").orElse(-1), triggers.idOf("PASSIVE").orElse(-1));
        // §B maintained passive POTION buffs: permanent-while-worn + suppression-aware + self-healing. The
        // authority for passive potions (runs after the lifecycle diff); re-derives from live worn state each
        // refresh, so a DISABLE_ENCHANT drops exactly the right effects and the correct set is restored after.
        passiveEffects = new feature.trigger.PassiveEffectDriver(triggerDispatch, content, worn, suppression,
                tick::get, triggers.idOf("HELD").orElse(-1), triggers.idOf("PASSIVE").orElse(-1));
        // §6.6 set equip/remove: the authored per-set message on a completion transition PLUS the universal
        // equip/unequip sound+particle (one config for all sets; the dust takes the set's own colour).
        feature.trigger.SetMessageDriver setMessages = new feature.trigger.SetMessageDriver(content,
                (player, msg) -> { // split on \n (keep trailing empties) so a leading AND trailing blank line both render
                    for (String line : item.mint.ItemFactory.color(msg).split("\n", -1)) {
                        player.sendMessage(line);
                    }
                },
                () -> master.config().sets().messageUppercase(), // read live so a reload can flip it
                new feature.trigger.SetEquipEffects(() -> master.config().sets(), particleFx));

        // §L feature toggles gate listener registration at BOOT: handlers can't be cleanly re-bound mid-run,
        // so a toggle change needs a restart.
        MasterConfig.FeaturesSection features = master.config().features();

        getServer().getPluginManager().registerEvents(new CombatListener(dispatch), this);
        getServer().getPluginManager().registerEvents(
                new EquipListener(worn, content, passives, lifecycle, passiveEffects, setMessages), this);
        // §B instant DISABLE: when a player is suppressed, drop their now-disabled passive buffs at once and
        // schedule their restore at the window's end (the periodic sweep is only the safety net).
        suppression.onSuppress((playerId, durationTicks) -> {
            Player target = getServer().getPlayer(playerId);
            if (target != null) {
                Scheduling.onEntity(target, () -> passiveEffects.refresh(target));
                Scheduling.onEntityLater(target, durationTicks + 1L, () -> passiveEffects.refresh(target));
            }
        });
        if (features.souls()) {
            getServer().getPluginManager().registerEvents(new SoulListener(soulService), this);
            getServer().getPluginManager().registerEvents(new SoulInteractListener(soulService), this);
            getServer().getPluginManager().registerEvents(new SoulInventoryListener(soulService), this);
        } else {
            getLogger().info("souls feature disabled (config.yml features.souls) — soul listeners not registered");
        }
        getServer().getPluginManager().registerEvents(new TriggerListeners(triggerDispatch,
                () -> "ALL".equalsIgnoreCase(items.config().heroicOrDefault().reductionScope())), this); // §F reduction-scope
        // ITEM_DAMAGE lives in its own listener (the event is 1.9+; the legacy overlay is a no-op).
        getServer().getPluginManager().registerEvents(
                new feature.trigger.DurabilityTriggerListener(triggerDispatch), this);
        // A landing FALLING_BLOCK fires the IMPACT trigger on whoever it hit (druid Terrablender grass rain).
        getServer().getPluginManager().registerEvents(
                new feature.combat.FallingBlockListener(triggerDispatch), this);
        // EQUIP_SWAP (spooky's pumpkin helmet) — keep death/quit normal: restore the real piece, never the placeholder.
        getServer().getPluginManager().registerEvents(new feature.combat.TempEquipListener(), this);
        // Magma floor (devil's Hell's Kitchen) scorches the scene, not the health: cancel HOT_FLOOR in a hellfire zone.
        getServer().getPluginManager().registerEvents(new feature.combat.HellfireFloorListener(), this);
        getServer().getPluginManager().registerEvents(
                new EngineStoreListener(vars, suppression, knockback, keepOnDeath, teleblock, immune,
                        cooldowns, combo, soulService), this);
        // §C KEEP_ON_DEATH at NORMAL priority — earlier than HolyScrollListener (HIGH) — so an enchant-kept
        // death never spends a holy scroll.
        getServer().getPluginManager().registerEvents(new KeepOnDeathListener(keepOnDeath, tick::get), this);
        // Cosmic Enchants exotic-effect ports: TELEBLOCK cancels teleport, IMMUNE cancels damage while flagged.
        getServer().getPluginManager().registerEvents(new TeleblockListener(teleblock, tick::get), this);
        getServer().getPluginManager().registerEvents(new ImmuneListener(immune, tick::get), this);
        // §C KNOCKBACK_CONTROL: capability-probed onto modern-bukkit or legacy destroystokyo; inert on neither.
        KnockbackListener.Path knockbackPath = KnockbackListener.register(this, knockback, tick::get);
        getLogger().info("KNOCKBACK_CONTROL applier: " + knockbackPath);
        // §N (ADR-0026): Mental OWNS player knockback, so the vanilla applier is discarded for players; bind
        // its KnockbackApplyEvent so KNOCKBACK_CONTROL composes onto Mental's vector instead of being lost.
        MentalKnockbackBridge.Path mentalPath = MentalKnockbackBridge.register(
                this, knockback, tick::get, master.config().integrations().enabled("mental"));
        getLogger().info("Mental knockback coordination: " + mentalPath);
        // §I custom items do ONLY their intended action — suppress their vanilla mechanics (the orb's ender-eye
        // throw, a nametag renaming a mob, a food/potion-material item being consumed). Material-agnostic: keyed
        // off the OR of every economy/utility codec, NOT the material. Real enchanted GEAR is excluded (swords swing).
        java.util.function.Predicate<org.bukkit.inventory.ItemStack> isPluginItem = stack -> {
            if (stack == null || stack.getType() == org.bukkit.Material.AIR) {
                return false;
            }
            return soulService.isGem(stack) || scrolls.isScroll(stack) || scrolls.isGodlyTransmog(stack)
                    || holyScrolls.isHolyScroll(stack) || nametags.isNametag(stack) || slots.isSlotItem(stack)
                    || crystals.isCrystal(stack) || crystals.isExtractor(stack) || traks.isTrakGem(stack)
                    || heroics.isUpgrade(stack) || unopenedBooks.isUnopened(stack)
                    || carrierCodec.read(stack) != null; // enchant books, magic dust, white scroll
        };
        getServer().getPluginManager().registerEvents(new feature.guard.VanillaGuardListener(isPluginItem), this);
        getServer().getPluginManager().registerEvents(new CarrierListener(carriers, carrierCodec, particleFx), this);
        getServer().getPluginManager().registerEvents(new CrystalListener(crystals), this);
        getServer().getPluginManager().registerEvents(new HeroicListener(heroics), this);
        if (features.slots()) {
            getServer().getPluginManager().registerEvents(new SlotListener(slots), this);
        } else {
            getLogger().info("slots feature disabled (config.yml features.slots) — slot-expander apply not registered");
        }
        getServer().getPluginManager().registerEvents(new UnopenedBookListener(unopenedBooks), this);
        // §L scrolls feature gate.
        if (features.scrolls()) {
            getServer().getPluginManager().registerEvents(new ScrollListener(scrolls), this);
            getServer().getPluginManager().registerEvents(new HolyScrollListener(holyScrolls, keptItems), this);
            getServer().getPluginManager().registerEvents(new NametagListener(nametags), this);
            feature.scroll.NametagAnvil.installPreview(this, nametags); // modern: colour the anvil result preview (no-op on 1.8.9)
            getServer().getPluginManager().registerEvents(new feature.trak.TrakListener(traks), this);
        } else {
            getLogger().info("scrolls feature disabled (config.yml features.scrolls) — scroll listeners not registered");
        }
        // Heroic durability (§F): a heroic item's per-item durability chance cancels item-damage events.
        getServer().getPluginManager().registerEvents(
                new feature.heroic.HeroicDurabilityListener(codec, new java.util.Random()), this);

        // Arm players already online (a plugin /reload with players on); a fresh boot has none. Normal joins
        // are armed by EquipListener via PlayerJoinEvent.
        for (Player player : getServer().getOnlinePlayers()) {
            Scheduling.onEntity(player, () -> {
                var state = worn.refresh(player, content.snapshot());
                passives.arm(player, state);     // §B REPEATING
                lifecycle.refresh(player, state); // §B HELD/PASSIVE
                passiveEffects.refresh(player);   // §B maintained passive potions
            });
        }

        // §B passive-potion maintenance sweep: re-derive every online player's permanent passive buffs so they
        // never lapse and self-heal after a death/milk/other clear. The time-critical paths (equip, respawn,
        // suppression) refresh instantly; this is the safety net. The global task only DISPATCHES per-entity
        // work (Folia-correct) — it touches no entity itself.
        Scheduling.repeatingGlobal(PASSIVE_SWEEP_TICKS, PASSIVE_SWEEP_TICKS, () -> {
            for (Player player : getServer().getOnlinePlayers()) {
                Scheduling.onEntity(player, () -> passiveEffects.refresh(player));
            }
        });

        // The §L config sources reload in the SAME transaction as content (§L-4): each parses off-thread,
        // and the reloader commits all-or-nothing — any error keeps the previous state of EVERYTHING.
        List<platform.content.ReloadStep> reloadSteps = List.of(
                () -> { var c = ItemsLoader.load(itemsRoot); return new platform.content.ReloadStep.Built(
                        c.diagnostics(), () -> items.publish(c)); },
                () -> { var c = MasterConfigLoader.load(configFile); return new platform.content.ReloadStep.Built(
                        c.diagnostics(), () -> master.publish(c)); },
                () -> { var c = LangLoader.load(langFile); return new platform.content.ReloadStep.Built(
                        c.diagnostics(), () -> lang.publish(c)); },
                () -> { var c = MenusLoader.load(menusRoot); return new platform.content.ReloadStep.Built(
                        c.diagnostics(), () -> menusHolder.publish(c)); });

        // On a clean swap this hook advances the gen-keyed caches and re-resolves every online player.
        reloader = new ContentReloader(content, () -> compiler, contentRoot, 0, published -> {
            itemViews.reload(published.snapshot().generation());
            executor.bindQuarantine(quarantineFor(published.snapshot())); // §10 fresh per snapshot — a fixed edit clears the block
            getServer().getPluginManager().callEvent(new StarEnchantsReloadEvent(
                    published.snapshot().generation(), published.snapshot().abilityCount()));
            if (master.config().reload().reResolvePlayers()) { // §L config.yml reload.re-resolve-players
                for (Player player : getServer().getOnlinePlayers()) {
                    // Re-arm against the new snapshot per player (a repeating task's period may have changed).
                    Scheduling.onEntity(player, () -> {
                        var state = worn.refresh(player, published.snapshot());
                        passives.arm(player, state);
                        lifecycle.refresh(player, state);
                        passiveEffects.refresh(player);
                    });
                }
            }
        }, reloadSteps);

        // §L auto-reload (config.yml reload.auto-seconds; ≤ 0 = off). Armed once at boot — interval change needs a restart.
        int autoSeconds = master.config().reload().autoSeconds();
        if (autoSeconds > 0) {
            long period = autoSeconds * 20L;
            Scheduling.repeatingGlobal(period, period, () -> reloader.reload(result -> { }));
            getLogger().info("auto-reload armed: every " + autoSeconds + "s");
        }

        // GUIs on the shared menu framework (§K). Menus open on the player's region thread (Folia open-hop).
        // Enchant-icon names are styled by the enchant-book name template, so a menu name matches the book.
        java.util.function.Supplier<String> bookName = () -> items.config().enchantBookOrDefault().name();
        EnchantMenu applyMenu = new EnchantMenu(content, enchanter,
                player -> worn.refresh(player, content.snapshot()), caps, menusHolder::config, bookName);
        // Hoisted so the physical godly-transmog gesture listener can open it bound to a clicked piece (§I/§K).
        GodlyTransmogMenu transmogMenu = new GodlyTransmogMenu(content, codec, scrolls, caps, menusHolder::config);
        // The operator "mint anything" catalogue (ADR-0030) — driven by the live tier list + trak kinds.
        MintCatalog mintCatalog = new MintCatalog(content, soulService, slots, heroics, crystals, scrolls,
                holyScrolls, nametags, carriers, traks, unopenedBooks);
        // The hubs look siblings up live from the registry, so registration order is irrelevant.
        MenuRegistry menus = new MenuRegistry();
        menus.register(new UserHubMenu(menus, caps, menusHolder::config))                  // /enchants player landing
                .register(new OperatorConsoleMenu(menus, reloader, messages, caps, menusHolder::config)) // /se menu
                .register(new MintMenu(mintCatalog, caps, messages, menusHolder::config))  // operator: mint anything
                .register(applyMenu)
                .register(new EnchantsBrowserMenu(content, caps, menusHolder::config, bookName)) // tier → enchant catalog
                .register(new SetsBrowserMenu(content, enchanter, caps, messages, menusHolder::config)) // sets → pieces → mint
                .register(new CrystalsBrowserMenu(content, crystals, caps, messages, menusHolder::config)) // browse + mint
                .register(new ReferenceBrowserMenu(caps, menusHolder::config))             // effects/selectors/…
                .register(transmogMenu)                                                    // reorder lore (held or bound)
                .register(new EnchanterMenu(content, unopenedBooks, caps, messages, menusHolder::config)) // buy books
                .register(new AlchemistMenu(carriers, caps, messages, menusHolder::config)) // combine books → +1
                .register(new TinkererMenu(carriers, caps, messages, menusHolder::config))  // salvage book → XP
                .register(new AdminBrowserMenu(content, carriers, caps, messages, menusHolder::config)); // admin grant
        getServer().getPluginManager().registerEvents(new MenuListener(), this);
        // §I/§K physical godly-transmog gesture — scroll family, so it shares the features.scrolls() boot gate.
        if (features.scrolls()) {
            getServer().getPluginManager().registerEvents(
                    new feature.menu.GodlyTransmogListener(scrolls, transmogMenu, codec), this);
        }
        // ADR-0030 user entry: /enchants opens the player hub (open to all; the hub's targets are perm-free).
        // Registered on the server command map like /splitsouls, so it needs no plugin.yml command entry.
        try {
            bootstrap.compat.Commands.register(getServer(), "starenchants",
                    new UserMenuCommand("enchants", menus, messages));
        } catch (Throwable t) {
            getLogger().warning("could not register /enchants (use /se menu hub instead): " + t);
        }

        // Config packs (ADR-0023). /se pack apply pairs the on-disk swap with the transactional reloader.
        PackStore packs = new PackStore(getDataFolder().toPath());

        PluginCommand command = getCommand("se");
        if (command != null) {
            SeCommand seCommand = new SeCommand(reloader, enchanter,
                    player -> worn.refresh(player, content.snapshot()), soulService,
                    getDataFolder().toPath().resolve("migrated"), menus, content,
                    head -> migrateSpecs.lookup(head).orElse(null), carriers, crystals, heroics, slots,
                    scrolls, unopenedBooks, holyScrolls, nametags, traks, packs, codec, carrierCodec,
                    () -> master.config().slots().base(), messages, contentRoot);
            command.setExecutor(seCommand);
            command.setTabCompleter(seCommand);
        }

        // §D /splitsouls — a top-level alias for /se split (the soul gem's lore advertises it). Registered on
        // the server command map like the command-trigger, so it needs no plugin.yml entry.
        try {
            bootstrap.compat.Commands.register(getServer(), "starenchants",
                    new feature.soul.SplitSoulsCommand("splitsouls", soulService, messages));
        } catch (Throwable t) {
            getLogger().warning("could not register /splitsouls (use /se split instead): " + t);
        }

        // §B COMMAND trigger: dynamic name can't live in plugin.yml, so register on the server command map
        // (guarded — an inaccessible map just leaves it unfireable). A name change needs a restart.
        var commandTrigger = master.config().commandTrigger();
        if (commandTrigger.enabled()) {
            try {
                bootstrap.compat.Commands.register(getServer(), "starenchants", new CommandTriggerCommand(
                        commandTrigger.name(), commandTrigger.description(), triggerDispatch,
                        messages.format("command.not-a-player")));
                getLogger().info("command-trigger registered: /" + commandTrigger.name());
            } catch (Throwable t) {
                getLogger().warning("could not register the command-trigger '/" + commandTrigger.name()
                        + "' (COMMAND enchants will not be fireable by command): " + t);
            }
        }
    }

    @Override
    public void onDisable() {
        // §B: repeating tasks outlive a /reload (other stores self-evict by TTL), so cancel them explicitly.
        if (passives != null) {
            passives.disarmAll();
        }
        if (lifecycle != null) {
            lifecycle.clearAll(); // forget started HELD/PASSIVE buffs (the driver is discarded across a reload)
        }
        if (passiveEffects != null) {
            passiveEffects.clearAll(); // forget the maintained-passive owned ledger (re-derived on next sweep)
        }
        if (soulParticles != null) {
            soulParticles.stop(); // cancel the §D while-active soul aura task
        }
        engine.sink.FallingBlockCasts.clearAll(); // forget any in-flight falling-block impact bindings
        engine.sink.CombatTag.clearAll(); // forget combat tags (supreme's out-of-combat fly)
        engine.sink.DamageMarks.clearAll(); // forget damage marks (reaper's Mark of the Reaper)
        engine.sink.OwnerZones.clearAll(); // forget owner zones (devil's Hell's Kitchen hellfire zones)
        engine.sink.TempEquip.clearAll(); // forget temporary equipment swaps (spooky's pumpkin helmet)
        if (metrics != null) {
            metrics.shutdown(); // stop the bStats submit thread so it doesn't outlive a /reload
        }
    }

    public ContentHolder content() {
        return content;
    }

    // §10: three faults of one ability disable it for the snapshot's life — enough to distinguish a genuinely
    // broken content unit from a one-off transient, without letting it spam every hit.
    private static final int QUARANTINE_THRESHOLD = 3;

    /** A fresh quarantine for {@code snapshot}: dense-id fault counters keyed to this snapshot's SourceMap/keys. */
    private static AbilityQuarantine quarantineFor(Snapshot snapshot) {
        return new AbilityQuarantine(snapshot.sourceMap(), snapshot.stableKeys(), QUARANTINE_THRESHOLD);
    }

    /**
     * The world-access seam for selectors (§3.6). All run synchronously on the firing thread, so each
     * touch is region-correct on Folia.
     */
    private static AreaScan areaScan() {
        return new AreaScan() {
            @Override
            public Iterable<LivingEntity> nearbyLiving(Location center, double radius) {
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
            }

            @Override
            public Player playerByName(String name) {
                return name == null || name.isBlank() ? null : org.bukkit.Bukkit.getPlayerExact(name);
            }

            @Override
            public LivingEntity entityInSight(Player from, double maxDistance) {
                if (from == null) {
                    return null;
                }
                Entity hit = bootstrap.compat.Targets.targetEntity(from, (int) Math.ceil(maxDistance));
                return hit instanceof LivingEntity living ? living : null;
            }

            @Override
            public Location targetBlock(Player from, double maxDistance) {
                if (from == null) {
                    return null;
                }
                org.bukkit.block.Block block = bootstrap.compat.Targets.targetBlock(from, (int) Math.ceil(maxDistance));
                return block == null ? null : block.getLocation();
            }

            @Override
            public List<Location> vein(Location start, int limit) {
                if (start == null || start.getWorld() == null || limit <= 0) {
                    return List.of();
                }
                World world = start.getWorld();
                org.bukkit.Material match;
                try {
                    match = world.getBlockAt(start).getType();
                } catch (RuntimeException offRegion) {
                    return List.of(); // a cross-region/unloaded read on Folia — bail rather than crash
                }
                if (feature.compat.Mats.isAir(match)) {
                    return List.of();
                }
                // 6-neighbour flood-fill of the same material, capped at `limit`. Guarded reads mean a
                // cross-region/unloaded block on Folia truncates the vein (best-effort, never crashes).
                List<Location> out = new ArrayList<>();
                java.util.Set<Long> seen = new java.util.HashSet<>();
                java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
                int[] origin = {start.getBlockX(), start.getBlockY(), start.getBlockZ()};
                queue.add(origin);
                seen.add(packBlock(origin[0], origin[1], origin[2]));
                int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
                while (!queue.isEmpty() && out.size() < limit) {
                    int[] c = queue.poll();
                    org.bukkit.Material type;
                    try {
                        type = world.getBlockAt(c[0], c[1], c[2]).getType();
                    } catch (RuntimeException offRegion) {
                        continue;
                    }
                    if (type != match) {
                        continue;
                    }
                    out.add(new Location(world, c[0], c[1], c[2]));
                    for (int[] d : dirs) {
                        int nx = c[0] + d[0];
                        int ny = c[1] + d[1];
                        int nz = c[2] + d[2];
                        if (seen.add(packBlock(nx, ny, nz))) {
                            queue.add(new int[] {nx, ny, nz});
                        }
                    }
                }
                return out;
            }
        };
    }

    /** Pack block coords into a collision-free long (26/12/26 bits) for the flood-fill visited set. */
    private static long packBlock(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    /**
     * Fires {@link EnchantActivateEvent} per proc, on the firing thread (the player's region on Folia).
     * {@code key} comes resolved against the snapshot whose abilities fired — re-reading the live holder
     * could mis-name the event after a concurrent {@code /se reload}; a null key is skipped, not faked.
     */
    private void fireActivation(String key, Ability ability, ActivationContext context) {
        Player actor = context.actor();
        if (actor == null || key == null) {
            return;
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

    /** Extract the bundled defaults on first boot; never overwrites an operator's edits. */
    private void saveDefaults() {
        saveDefaultTree("packs"); // ADR-0023: always keep the pack LIBRARY current (newly-shipped packs appear)
        // When a pack owns the config surface, the bundled defaults must NOT be re-laid over it: the per-file
        // top-up below would otherwise re-add every default file the pack omits, so the default content
        // reappears alongside the pack after a restart. The pack is the authority while it is active.
        if (pack.PackStore.activePack(getDataFolder().toPath()).isPresent()) {
            return;
        }
        saveDefaultFile("config.yml");
        saveDefaultFile("lang.yml");
        saveDefaultTree("content");
        saveDefaultTree("items");
        saveDefaultTree("menus");
    }

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

    /** Extract one bundled tree, driven by its {@code <root>/index.txt} manifest. */
    private void saveDefaultTree(String root) {
        Path dataFolder = getDataFolder().toPath();
        for (String relative : shippedPaths(root)) {
            String resource = root + "/" + relative;
            if (Files.exists(dataFolder.resolve(resource))) {
                continue;
            }
            try {
                saveResource(resource, false);
            } catch (RuntimeException missing) {
                getLogger().warning("could not save default '" + resource + "': " + missing.getMessage());
            }
        }
    }

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
