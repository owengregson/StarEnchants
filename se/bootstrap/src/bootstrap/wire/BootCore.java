package bootstrap.wire;

import api.event.EnchantActivateEvent;
import compile.Compiler;
import compile.SpecRegistry;
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
import compile.model.Ability;
import compile.model.Snapshot;
import engine.boot.ContentCompiler;
import engine.effect.EffectKind;
import engine.effect.EffectRegistry;
import engine.effect.kind.BuiltinEffects;
import engine.interact.SoulPool;
import engine.pipeline.ActivationPipeline;
import engine.run.AbilityExecutor;
import engine.run.AbilityQuarantine;
import engine.run.ActivationContext;
import engine.run.AreaScan;
import engine.selector.kind.BuiltinSelectors;
import engine.sink.SinkEnv;
import engine.stores.EngineStores;
import engine.stores.SoulModeStore;
import engine.trigger.BuiltinTriggers;
import engine.trigger.TriggerRegistry;
import feature.apply.ItemEnchanter;
import feature.combat.CombatDispatch;
import feature.soul.SoulService;
import feature.trigger.TriggerDispatch;
import item.codec.AppliedSlot;
import item.codec.CarrierCodec;
import item.codec.CombatCodec;
import item.codec.ItemKeys;
import item.codec.ItemStateStore;
import item.codec.SoulCodec;
import item.codec.TrakCodec;
import item.mint.VanillaEnchants;
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
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import java.util.function.BiPredicate;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import platform.caps.Capabilities;
import platform.caps.Regions;
import platform.economy.EconomyProvider;
import platform.economy.EconomyService;
import platform.item.ItemGroups;
import platform.lang.Messages;
import platform.protect.ProtectionProvider;
import platform.protect.ProtectionProviders;
import platform.protect.ProtectionService;
import platform.resolve.RegistryResolvers;
import platform.sched.Scheduling;
import schema.diag.DiagCode;
import schema.diag.Diagnostic;
import schema.diag.Diagnostics;
import schema.diag.Source;

/**
 * The feature-neutral substrate (ADR-0047): everything the composition root builds ONCE and every
 * {@code FeatureModule} consumes — platform probe, the {@code bootstrap.compat.EraBindings} seam, the config
 * sources, the item read path, and the engine spine (stores, pipeline, dispatch). Extracted VERBATIM from the
 * old {@code onEnable} construction so a module ctor reads {@code core.*} instead of re-building it; nothing
 * feature-shaped (listeners, mint, drivers) lives here — that is the {@link Modules} registry's axis.
 *
 * <p>Construction only, zero registrations: the reordering lemma (no event fires during enable) makes the
 * move observationally free. {@code soulService} rides the engine spine because the pipeline's gate-10 spend,
 * the sink's soul debit, and both dispatchers' binding lookup all consume it — the souls module then declares
 * its listeners/aura/mint over {@code core.soulService()}.
 */
public final class BootCore {

    // §10 three faults of one ability disable it for the snapshot's life — enough to distinguish a genuinely
    // broken content unit from a one-off transient without letting it spam every hit.
    private static final int QUARANTINE_THRESHOLD = 3;

    private final JavaPlugin plugin;

    // platform
    private final Capabilities caps;
    private final AtomicLong tick;
    private final bstats.Metrics metrics;

    // era seams (ADR-0044)
    private final bootstrap.compat.EraServices bindings;
    private final feature.fx.ParticleFx particleFx;
    private final ItemStateStore store;
    private final feature.compat.Hands hands;
    private final feature.compat.DropControl dropControl;
    private final feature.compat.Projectiles projectiles;
    private final feature.compat.Sounds sounds;
    private final feature.scroll.AnvilRename anvilRename;

    // config
    private final RegistryResolvers resolvers;
    private final CopyOnWriteArrayList<EffectKind> addonKinds;
    private final Supplier<EffectRegistry> effectRegistry;
    private final Supplier<Compiler> compilerFactory;
    private final ContentHolder content;
    private final ItemsHolder items;
    private final MasterConfigHolder master;
    private final LangHolder lang;
    private final Messages messages;
    private final MenusHolder menusHolder;
    private final Path contentRoot;
    private final Path itemsRoot;
    private final Path configFile;
    private final Path langFile;
    private final Path menusRoot;

    // item spine
    private final CombatCodec codec;
    private final feature.guard.StationGuardRules stationGuardRules;
    private final ItemViewCache itemViews;
    private final TriggerRegistry triggers;
    private final WornResolver wornResolver;
    private final WornStateStore worn;
    private final AppliedSlot appliedSlot;
    private final CarrierCodec carrierCodec;
    private final TrakCodec trakCodec;
    private final item.codec.PetCodec petCodec;
    private final feature.pet.PetArmedStore petArmedStore;
    private final item.codec.MaskCodec maskCodec;
    private final item.codec.ReforgeCodec reforgeCodec;
    private final LoreRenderer lore;
    private final ItemGroups itemGroups;
    private final Consumer<ItemStack> recompose;
    private final Random rolls;
    private final VanillaEnchants vanillaEnchants;
    private final ItemEnchanter enchanter;

    // engine spine
    private final SoulPool soulPool;
    private final SoulModeStore soulModes;
    private final SoulService soulService;
    private final EngineStores stores;
    private final ProtectionService protection;
    private final ActivationPipeline.Guard protectionGuard;
    private final EconomyService economy;
    private final SinkEnv sinkEnv;
    private final AbilityExecutor executor;
    private final SpecRegistry migrateSpecs;
    private final CombatDispatch dispatch;
    private final TriggerDispatch triggerDispatch;

    /** Build the whole substrate for {@code plugin} — the old onEnable construction, relocated verbatim. */
    public static BootCore boot(JavaPlugin plugin) {
        return new BootCore(plugin);
    }

    private BootCore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.caps = Capabilities.probe(plugin.getServer());
        Scheduling.init(plugin, caps);
        plugin.getLogger().info("StarEnchants — " + caps + ", scheduling "
                + Scheduling.backend().getClass().getSimpleName());

        // bStats (id 32197): the vendored Metrics is Folia-aware. Owners opt out via plugins/bStats/config.yml.
        this.metrics = new bstats.Metrics(plugin, 32197);

        // Monotonic game-tick counter for cooldown timing.
        this.tick = new AtomicLong();
        Scheduling.repeatingGlobal(0L, 1L, tick::incrementAndGet);

        saveDefaults();
        this.contentRoot = plugin.getDataFolder().toPath().resolve("content");
        this.itemsRoot = plugin.getDataFolder().toPath().resolve("items");
        this.configFile = plugin.getDataFolder().toPath().resolve("config.yml");
        this.langFile = plugin.getDataFolder().toPath().resolve("lang.yml");
        this.menusRoot = plugin.getDataFolder().toPath().resolve("menus");

        this.resolvers = new RegistryResolvers();
        // Add-on effect kinds (ADR-0038): registered at runtime through StarEnchantsApi, folded into the effect
        // registry on EVERY build so an add-on head is both compilable and runnable and survives /se reload.
        this.addonKinds = new CopyOnWriteArrayList<>();
        Collection<EffectKind> builtinEffects = BuiltinEffects.registry().kinds();
        this.effectRegistry = () -> {
            EffectRegistry.Builder b = EffectRegistry.builder();
            builtinEffects.forEach(b::register);
            addonKinds.forEach(b::register);
            return b.build();
        };
        // Same resolver instance every build (the §9 handle round-trip depends on it); only the effect spec set
        // changes as add-ons register, so a fresh compiler per reload is safe.
        Compiler compiler = ContentCompiler.production(resolvers, effectRegistry.get());
        // The era bindings (ADR-0044): the one seam carrying every version-specific service.
        this.bindings = new bootstrap.compat.EraBindings(resolvers);
        // {#RRGGBB} runtime form (ADR-0062): era-installed before anything translates/renders on this boot.
        platform.text.Colors.hexMode(bindings.hexMode());
        this.particleFx = bindings.particleFx();
        // The physical item-data layer (§4.2, ADR-0044): era store injected into every codec + the lore renderer.
        this.store = bindings.itemStateStore();
        // The feature.compat era seams (§4, ADR-0044): hand access, drop suppression, projectile typing, sound.
        this.hands = bindings.hands();
        this.dropControl = bindings.dropControl();
        this.projectiles = bindings.projectiles();
        this.sounds = bindings.sounds();
        this.anvilRename = bindings.anvilRename(); // §I nametag rename (§4 era seam)

        Library initial = loadInitial(compiler, contentRoot);
        this.content = new ContentHolder(initial);
        logLoad(initial);

        // The §L parallel config sources (items/, config.yml, lang.yml, menus/) — each an immutable reference
        // swapped in the same reload transaction, read live through suppliers.
        this.items = new ItemsHolder(ItemsLoader.load(itemsRoot));
        logItems(items.config());

        this.master = new MasterConfigHolder(MasterConfigLoader.load(configFile));
        logMaster(master.config());

        this.lang = new LangHolder(LangLoader.load(langFile));
        this.messages = new Messages(this.lang::lang,
                () -> master.config().messages().prefix(),
                () -> master.config().messages().feedback(),
                // §N PlaceholderAPI passthrough (ADR-0027): resolve other plugins' %…% when present, else identity.
                bindings.placeholderResolver(plugin, master.config().integrations()::enabled));

        this.menusHolder = new MenusHolder(MenusLoader.load(menusRoot));

        // Item read path: codec → ItemView cache → WornResolver → per-player WornStateStore.
        this.codec = new CombatCodec(ItemKeys.of().combat(), store);
        // Vanilla-station guard (G04/G05/G06 + the masked-helmet audit, ADR-0064): gear carrying stacked
        // plugin value (set membership, a mask, or crystals — the single-sourced pluginValueGear predicate)
        // is denied at the stations; the three toggles read live so a /se reload re-tunes them.
        this.stationGuardRules = new feature.guard.StationGuardRules(
                feature.guard.StationGuardRules.pluginValueGear(codec::read),
                () -> master.config().stations().anvilGuard(),
                () -> master.config().stations().grindstoneGuard(),
                () -> master.config().stations().smithingGuard());
        this.itemViews = new ItemViewCache(codec, initial.snapshot().generation());
        this.triggers = BuiltinTriggers.registry();
        // ADR-0052 pets: the identity/level/exp codec + the armed-window store, built here so the resolver's
        // pet source and the PetsModule share the ONE pair.
        this.petCodec = new item.codec.PetCodec(ItemKeys.of(), store);
        this.petArmedStore = new feature.pet.PetArmedStore();
        // ADR-0053 masks: the ONE mask-item identity codec, next to petCodec — shared by the MasksModule's mint,
        // apply/remove gestures, and the plugin-item guard. On-helmet mask state rides the combat blob, not here.
        this.maskCodec = new item.codec.MaskCodec(ItemKeys.of(), store);
        // ADR-0070 reforges: the ONE reforge-item identity codec, next to maskCodec — shared by the ReforgesModule's
        // mint, apply gesture, and the plugin-item guard. On-weapon reforge state rides the combat blob, not here.
        this.reforgeCodec = new item.codec.ReforgeCodec(ItemKeys.of(), store);
        this.wornResolver = new WornResolver(bindings.equipSource(), itemViews, triggers.count(),
                triggers.attackTriggers(), triggers.defenseTriggers(),
                () -> {                                            // §L per-feature master toggles (live)
                    MasterConfig.FeaturesSection ff = master.config().features();
                    return new WornResolver.Features(ff.enchants(), ff.sets(), ff.crystals(), ff.heroic(),
                            ff.masks(), ff.reforges());
                },
                () -> {                                            // §ADR-0035 non-stackable crystal keys (live)
                    java.util.Set<String> keys = new java.util.HashSet<>();
                    for (compile.load.CrystalDef def : content.library().crystals()) {
                        if (!def.stackable()) {
                            keys.add(def.key());
                        }
                    }
                    return keys;
                },
                // ADR-0052 the sixth source: hotbar pets, decided wholly by the pets feature (bracket + armed).
                new feature.pet.PetWornSource(() -> master.config().features().pets(), petCodec,
                        () -> content.library(), petArmedStore, tick::get));
        this.worn = new WornStateStore(wornResolver::resolve);

        // §I the applied-utility marker set, shared by white/holy scrolls and the trak gems (independent markers).
        this.appliedSlot = new AppliedSlot(ItemKeys.of().appliedSlot(), store);
        // Carrier economy (ADR-0016). Carrier PDC is separate from the combat blob, so it never decodes hot.
        this.carrierCodec = new CarrierCodec(ItemKeys.of().carrier(), ItemKeys.of().guarded(), store);
        // Trak counters (§I) — built before the renderer so the composer's trak SECTION reads them from state.
        this.trakCodec = new TrakCodec(ItemKeys.of().trakGem(),
                ItemKeys.of().trakBlocks(), ItemKeys.of().trakMobs(), ItemKeys.of().trakSouls(),
                ItemKeys.of().trakFish(), store);

        // ADR-0040 sectioned composition: protection + trak lines render from marker/counter STATE (never parsed
        // back), so the composer owns every section in one deterministic pass.
        java.util.function.Function<ItemStack, java.util.List<String>> protectionLinesFn = stack ->
                item.render.ProtectionLore.lines(carrierCodec.isGuarded(stack),
                        appliedSlot.holds(stack, AppliedSlot.HOLY),
                        items.config().whiteScrollOrDefault().protectedLine(),
                        items.config().scrollsOrDefault().holy().protectedLine());
        java.util.function.Function<ItemStack, java.util.List<String>> trakLinesFn = stack ->
                feature.trak.TrakService.countLines(stack, trakCodec, appliedSlot, items.config().traksOrDefault());
        // MIGRATION-ONLY (ADR-0040): the visible-text classifiers survive to let the composer's one-time legacy
        // shim recognise a pre-composer protection/trak line on an unmarked item; the permanent path never uses them.
        java.util.function.Predicate<String> legacyLoreLine = line ->
                item.render.ProtectionLore.isProtectionLine(line,
                        items.config().whiteScrollOrDefault().protectedLine(),
                        items.config().scrollsOrDefault().holy().protectedLine())
                || feature.trak.TrakService.isCountLine(line, items.config().traksOrDefault());

        // Cold apply path (ADR-0040): the composer's section wiring, as named Config fields. Lookups read the
        // CURRENT library, so a reload re-renders against new content.
        this.lore = new LoreRenderer(LoreRenderer.Config
                .of(() -> loreStyle(master.config()), key -> {
                    // ADR-0070 (identity rework): the on-weapon reforge line's {NAME} is the reforge's
                    // colour-styled BOLD display — the mask rule; the template's frame supplies its own reset
                    // AFTER the token (`{NAME}&r&6&l`), so no redundant leading `&r` exists for the ItemMeta
                    // legacy round-trip to drop.
                    compile.load.ReforgeDef reforge = content.library().reforgeDefOf(key);
                    if (reforge != null) {
                        return reforge.color() + "&l" + reforge.display();
                    }
                    // ADR-0053: the on-helmet mask line's {NAME} wants the mask's colour-styled bold display; a
                    // masks/<stem> key isn't in displayNameOf's enchant/crystal/set chain, so resolve it first —
                    // null for any other unknown key stays the renderer's unknown-label path.
                    compile.load.MaskDef mask = content.library().maskDefOf(key);
                    if (mask != null) {
                        return mask.color() + "&l" + mask.display();
                    }
                    return content.library().displayNameOf(key);
                })
                .withEnchantColorOf(key -> {        // per-enchant rarity-tier colour (ADR-0016 §2); null → universal
                    String tier = content.library().tierOf(key);
                    if (tier == null) {
                        return null;
                    }
                    compile.load.TierRegistry.Tier t = content.library().tiers().tier(tier);
                    return t != null && !t.color().isBlank() ? t.color() : null;
                })
                .withSetLore(new LoreRenderer.SetLore() { // §6.6 set-member lore, read live from the current library
                    @Override public java.util.List<String> armor(String setKey) {
                        compile.load.SetDef def = content.library().setDefOf(setKey);
                        return def != null ? def.armorLore() : java.util.List.of();
                    }

                    @Override public java.util.List<String> weapon(String setKey) {
                        compile.load.SetDef def = content.library().setDefOf(setKey);
                        return def != null ? def.weaponLore() : java.util.List.of();
                    }
                })
                .withProtectionLines(protectionLinesFn) // §I applied-scroll PROTECTED lines, from marker state
                .withTrakLines(trakLinesFn)             // §I applied-trak count lines, from marker + counter state
                .withLegacyLoreLine(legacyLoreLine)     // ADR-0040 one-time migration recogniser (unmarked items only)
                .withCountSuffix(() -> items.config().scrollsOrDefault().transmog().nameSuffix()) // §I count suffix
                .withBaseSlots(() -> master.config().slots().base())   // §H base slots → orb "Enchantment Slots" total
                .withSlotsLine(() -> master.config().slots().loreLine()) // §H orb "Enchantment Slots" line template
                .withHeroicLine(() -> items.config().heroicOrDefault().loreLine()) // §F HEROIC line template
                .withCrystalLine(() -> items.config().crystalOrDefault().loreWhileOnItem())        // §E on-gear line
                .withCrystalLineMulti(() -> items.config().crystalOrDefault().loreWhileOnItemMulti()) // §E merged
                .withMaskLine(() -> items.config().maskOrDefault().loreWhileOnItem()) // ADR-0053 on-helmet mask line
                .withReforgeLine(() -> items.config().reforgeOrDefault().loreWhileOnItem()), // ADR-0070 on-weapon line
                store); // ADR-0044 the item-data store backs the renderer's composer-migration marker
        this.itemGroups = ItemGroups.standard();                 // §I shared by the enchanter + trak gems
        // ADR-0040 the ONE recompose seam: a feature mutates PDC then asks the composer to re-render from state.
        this.recompose = gear -> lore.apply(gear, codec.read(gear));
        // §6.6 set-piece base enchants resolve cross-version behind the era seam as instance wiring (ADR-0047).
        this.vanillaEnchants = new VanillaEnchants(bindings.enchantResolver());
        this.enchanter = new ItemEnchanter(codec, lore, content, itemGroups,
                () -> master.config().slots().base(),          // §H base enchant slots
                () -> master.config().crystals().slots(),      // §E per-item crystal slots (entries)
                () -> master.config().crystals().maxMerge(),   // §E components per entry (merge cap)
                () -> master.config().reforges().weaponGroups(), // ADR-0070 reforge weapon-groups (apply gate)
                messages, vanillaEnchants);                    // §L ApplyResult strings + §6.6 vanilla enchants

        // ONE RNG for every apply/mint economy — injected so rolls are stubbable (Rolls).
        this.rolls = new Random();

        // Souls (§D): the per-player cross-gem SoulPool is the spend authority. The SoulService owns it and is
        // ALSO the pipeline's gate-10 SoulSpender, so a gate-10 spend and the holder-thread drain share one pool.
        // It rides the engine spine because the sink debit + both dispatchers' binding lookup consume it.
        this.soulPool = new SoulPool();
        this.soulModes = new SoulModeStore(); // shared by the service + the §D while-active aura driver
        this.soulService = new SoulService(soulPool, soulModes,
                new SoulCodec(ItemKeys.of().soul(), store), () -> items.config().soulGemOrDefault(),
                () -> master.config().souls().depositOnAnyKill(), messages, particleFx, hands, sounds,
                // §I a gem can carry the holy keep-marker; combine/split carry it and the re-render appends its
                // protected line — the SAME marker seam + protection-line function the LoreRenderer uses.
                appliedSlot, protectionLinesFn);

        // ONE shared aggregate of every per-player engine store: an effect writes a store through the per-event
        // sink and a separate reader reads it back — so it must be the SAME instance everywhere.
        this.stores = EngineStores.fresh();
        // The soul service keeps the per-player total; hand it the shared store so the PAPI feed and the
        // %actor.souls%/%victim.souls% facts read ONE number instead of two caches that can drift.
        this.soulService.soulTotals(stores.soulTotals());

        // §5.4 offline-state sweep (F03): the quit sweep RETAINS a leaving player's live combat windows against
        // the monotonic tick (so a relog can't skip a cooldown or shed a landed debuff); those entries are only
        // read again on rejoin, so a periodic global-thread sweep evicts elapsed ones to bound a never-returner.
        // Folia-safe — pure concurrent-map ops, no entity/world access. Armed once at boot (restart to re-tune).
        int sweepPeriod = master.config().engine().offlineStateSweepTicks();
        if (sweepPeriod > 0) {
            Scheduling.repeatingGlobal(sweepPeriod, sweepPeriod, () -> stores.evictElapsed(tick.get()));
        }

        // Protection / region gate (gate 2): bundled providers (§N, ADR-0027) + ServicesManager; none ⇒ allow.
        List<ProtectionProvider> protectionProviders = new ArrayList<>();
        if (master.config().integrations().protection()) {
            protectionProviders.addAll(
                    bindings.protectionProviders(plugin, master.config().integrations()::enabled));
            protectionProviders.addAll(
                    ProtectionProviders.discover(plugin.getServer(), System.getLogger("StarEnchants.Protection")));
        } // §L config.yml integrations.protection: false → gate against none
        this.protection = new ProtectionService(protectionProviders);
        if (protection.providerCount() > 0) {
            plugin.getLogger().info("protection gate active with " + protection.providerCount() + " provider(s)");
        }
        this.protectionGuard = (ability, activation) -> {
            Location where = activation.location();
            return where == null || protection.allows(activation.actor(), where);
        };

        // fireActivation fires EnchantActivateEvent per proc — Bukkit-aware here, so the engine stays event-free.
        EffectRegistry effects = effectRegistry.get();
        // §L global message-on-activate: the holder ("BY you") + the other party ("ON you") get a configured line.
        feature.combat.ActivationMessenger activationMessenger = new feature.combat.ActivationMessenger(
                () -> master.config().messageOnActivate(), () -> master.config().pets(), content);
        this.executor = new AbilityExecutor(effects, BuiltinSelectors.registry(),
                new ActivationPipeline(stores.cooldowns(), soulService, stores.suppression(), protectionGuard,
                        ActivationPipeline.Guard.ALLOW, stores.why()), // ADR-0045: record every gate walk
                areaScan(bindings), (key, ability, context) -> {
                    if (key == null) {
                        return; // a null key is skipped, not faked
                    }
                    fireActivation(key, ability, context);                 // EnchantActivateEvent (public API)
                    activationMessenger.onActivate(key, ability, context); // §L the configured BY/ON lines
                });
        // §10 runtime quarantine, bound to the live snapshot and rebound per reload (see the reload module).
        executor.bindQuarantine(quarantineFor(content.snapshot()));
        stores.why().generation(content.snapshot().generation()); // ADR-0045: stamp records so /se why resolves
        // The effect-head → ParamSpec lookup the migrators use to write verbose v2 effects (ADR-0016).
        this.migrateSpecs = effects.specRegistry();
        // Economy bridge for MODIFY_MONEY (global thread): bundled Vault (§N, ADR-0027) → ServicesManager → no-ops.
        if (master.config().integrations().economy()) {
            EconomyProvider bundled = bindings.economyProvider(plugin, master.config().integrations()::enabled);
            this.economy = bundled != null
                    ? new EconomyService(bundled)
                    : EconomyService.discover(plugin.getServer(), System.getLogger("StarEnchants.Economy"));
        } else {
            this.economy = EconomyService.NONE; // §L integrations.economy: false → money effects are no-ops
        }
        if (economy.present()) {
            plugin.getLogger().info("economy provider active");
        }
        // §N anti-cheat movement exemption for engine-applied VELOCITY/TELEPORT — rides the SinkEnv (ADR-0047).
        Consumer<Player> movementExemption = bindings.antiCheatExemption(
                plugin, master.config().integrations()::enabled, System.getLogger("StarEnchants.AntiCheat"));
        // ADR-0052 STRIP_SCROLL's view of on-item protection: marker codecs + the ADR-0040 recompose, injected
        // so the engine never names AppliedSlot/CarrierCodec. White clears BOTH its marker and the guard byte
        // (they are written together); the recompose makes the PROTECTED line vanish before the write-back.
        engine.sink.GearProtection gearProtection = new engine.sink.GearProtection() {
            @Override
            public boolean isProtected(ItemStack stack, boolean holy) {
                return holy ? appliedSlot.holds(stack, AppliedSlot.HOLY) : carrierCodec.isGuarded(stack);
            }

            @Override
            public boolean strip(ItemStack stack, boolean holy) {
                if (!isProtected(stack, holy)) {
                    return false;
                }
                if (holy) {
                    appliedSlot.release(stack, AppliedSlot.HOLY);
                } else {
                    carrierCodec.setGuarded(stack, false);
                    appliedSlot.release(stack, AppliedSlot.WHITE_SCROLL);
                }
                recompose.accept(stack);
                return true;
            }
        };
        // The per-boot sink wiring: built ONCE and threaded to both dispatchers, so a sink write and its
        // separate-event reader always see the SAME stores (no split-brain). The interest cap (ADR-0052 Fish)
        // reads the live pets section so /se reload re-tunes it.
        this.sinkEnv = SinkEnv.of(economy, soulService, stores, tick::get, movementExemption,
                () -> master.config().pets().maxPercentMoneyCap(), gearProtection,
                // ADR-0063: the worn LIGHTNING_MOD channel — live WornState + suppression, read per bolt emit.
                feature.trigger.LightningBoost.fn(content, worn, stores.suppression(), tick::get,
                        triggers.idOf("PASSIVE").orElse(-1)),
                bindings.playerVisibility(plugin)); // VIEWER_HIDE: the era seam, since the engine holds no plugin
        // mcMMO friendly-fire gate — ONE alliance predicate feeding both consumers. Combat suppression has
        // always used it; the targeting filters (@Aoe{filter=ENEMIES|ALLIES}) never had it installed, so they
        // treated a party-mate as an enemy while the damage gate spared them. Same predicate, both sides.
        BiPredicate<Player, Player> allied =
                bindings.mcmmoFriendlyFire(plugin, master.config().integrations()::enabled);
        CombatDispatch.friendlyFire(allied);
        engine.selector.kind.Allies.resolver(allied);
        // %victim.mobtype% from MythicMobs' internal name,
        engine.run.FactPopulator.entityTypeResolver(
                bindings.mythicMobType(plugin, master.config().integrations()::enabled));
        // itemsadder:… / oraxen:… custom-item materials in item/menu configs.
        item.mint.ItemFactory.customItemResolver(
                bindings.customItem(plugin, master.config().integrations()::enabled));
        // §L universal economy-item lore wrap width (lore.item-wrap), read live so a /se reload re-tunes it.
        item.mint.ItemFactory.itemWrapWidth(() -> master.config().lore().itemWrap());
        this.dispatch = new CombatDispatch(executor, bindings.sinkFactory(), bindings.actorProbe(), content, worn,
                triggers.idOf("ATTACK").orElseThrow(), triggers.idOf("DEFENSE").orElseThrow(),
                triggers.idOf("BOW").orElse(-1), triggers.idOf("TRIDENT").orElse(-1),
                triggers.idOf("HURT").orElse(-1),
                soulService::bindingFor, sinkEnv, new CombatDispatch.Caps(
                        () -> master.config().combat().maxBonusDamage(),          // §L combat.max-bonus-damage (live)
                        () -> master.config().combat().maxBonusReduction(),       // §L combat.max-bonus-reduction (live)
                        () -> master.config().combat().attackScale(),             // §L combat.attack-scale (live, ADR-0050 R2)
                        () -> master.config().combat().pvp(),                     // §L combat.pvp gate (live)
                        () -> master.config().combat().pve()), projectiles);      // §L combat.pve gate (live)
        // Non-combat triggers (MINE/KILL/FALL/FIRE/INTERACT*) — the events CombatDispatch does not cover.
        this.triggerDispatch = new TriggerDispatch(executor, bindings.sinkFactory(), bindings.actorProbe(), content,
                worn, triggers, soulService::bindingFor, sinkEnv, hands, dropControl);
        // On a clean swap the reloader rebinds the compiler per reload (not constant) so a newly registered add-on
        // head compiles; the resolver is reused, so the §9 handle round-trip holds (ADR-0038).
        this.compilerFactory = () -> ContentCompiler.production(resolvers, effectRegistry.get());
    }

    // ── accessors (the substrate a module consumes) ──────────────────────────────────────────────
    public JavaPlugin plugin() { return plugin; }

    public Capabilities caps() { return caps; }

    public AtomicLong tick() { return tick; }

    public bootstrap.compat.EraServices bindings() { return bindings; }

    public feature.fx.ParticleFx particleFx() { return particleFx; }

    public ItemStateStore store() { return store; }

    public feature.compat.Hands hands() { return hands; }

    public feature.compat.DropControl dropControl() { return dropControl; }

    public feature.compat.Projectiles projectiles() { return projectiles; }

    public feature.compat.Sounds sounds() { return sounds; }

    public feature.scroll.AnvilRename anvilRename() { return anvilRename; }

    public RegistryResolvers resolvers() { return resolvers; }

    public CopyOnWriteArrayList<EffectKind> addonKinds() { return addonKinds; }

    public Supplier<EffectRegistry> effectRegistry() { return effectRegistry; }

    public Supplier<Compiler> compilerFactory() { return compilerFactory; }

    public ContentHolder content() { return content; }

    public ItemsHolder items() { return items; }

    public MasterConfigHolder master() { return master; }

    public LangHolder lang() { return lang; }

    public Messages messages() { return messages; }

    public MenusHolder menusHolder() { return menusHolder; }

    public Path contentRoot() { return contentRoot; }

    public Path itemsRoot() { return itemsRoot; }

    public Path configFile() { return configFile; }

    public Path langFile() { return langFile; }

    public Path menusRoot() { return menusRoot; }

    public CombatCodec codec() { return codec; }

    public feature.guard.StationGuardRules stationGuardRules() { return stationGuardRules; }

    public ItemViewCache itemViews() { return itemViews; }

    public TriggerRegistry triggers() { return triggers; }

    public WornResolver wornResolver() { return wornResolver; }

    public WornStateStore worn() { return worn; }

    public AppliedSlot appliedSlot() { return appliedSlot; }

    public CarrierCodec carrierCodec() { return carrierCodec; }

    public TrakCodec trakCodec() { return trakCodec; }

    public item.codec.PetCodec petCodec() { return petCodec; }

    public feature.pet.PetArmedStore petArmedStore() { return petArmedStore; }

    public item.codec.MaskCodec maskCodec() { return maskCodec; }

    public item.codec.ReforgeCodec reforgeCodec() { return reforgeCodec; }

    public LoreRenderer lore() { return lore; }

    public ItemGroups itemGroups() { return itemGroups; }

    public Consumer<ItemStack> recompose() { return recompose; }

    public Random rolls() { return rolls; }

    public VanillaEnchants vanillaEnchants() { return vanillaEnchants; }

    public ItemEnchanter enchanter() { return enchanter; }

    public SoulPool soulPool() { return soulPool; }

    public SoulModeStore soulModes() { return soulModes; }

    public SoulService soulService() { return soulService; }

    public EngineStores stores() { return stores; }

    public ProtectionService protection() { return protection; }

    public EconomyService economy() { return economy; }

    public SinkEnv sinkEnv() { return sinkEnv; }

    public AbilityExecutor executor() { return executor; }

    public SpecRegistry migrateSpecs() { return migrateSpecs; }

    public CombatDispatch dispatch() { return dispatch; }

    public TriggerDispatch triggerDispatch() { return triggerDispatch; }

    /** The feature-neutral onDisable steps (the sink-static and driver stops are the modules' own). */
    public List<FeatureModule.Stop> coreStops() {
        return List.of(new FeatureModule.Stop("bStats", metrics::shutdown));
    }

    /** A fresh quarantine for {@code snapshot}: dense-id fault counters keyed to this snapshot's SourceMap/keys. */
    public static AbilityQuarantine quarantineFor(Snapshot snapshot) {
        return new AbilityQuarantine(snapshot.sourceMap(), snapshot.stableKeys(), QUARANTINE_THRESHOLD);
    }

    /**
     * The world-access seam for selectors (§3.6). All run synchronously on the firing thread, so each touch is
     * region-correct on Folia. {@code bindings} supplies the era targeting (modern raytrace / 1.8 LOS).
     */
    private static AreaScan areaScan(bootstrap.compat.EraServices bindings) {
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
                Entity hit = bindings.targetEntity(from, (int) Math.ceil(maxDistance));
                return hit instanceof LivingEntity living ? living : null;
            }

            @Override
            public Location targetBlock(Player from, double maxDistance) {
                if (from == null) {
                    return null;
                }
                org.bukkit.block.Block block = bindings.targetBlock(from, (int) Math.ceil(maxDistance));
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
                    Regions.swallowed("AreaScan.vein", offRegion);
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
                        Regions.swallowed("AreaScan.vein", offRegion);
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
     * {@code key} comes resolved against the snapshot whose abilities fired; a null key/actor is skipped.
     */
    private void fireActivation(String key, Ability ability, ActivationContext context) {
        Player actor = context.actor();
        if (actor == null || key == null) {
            return;
        }
        plugin.getServer().getPluginManager().callEvent(new EnchantActivateEvent(actor, key, ability.level()));
    }

    /**
     * The initial load, guaranteed not to throw out of enable — a content load fault boots empty with the fault
     * recorded as an E_LOAD_IO diagnostic and the stack logged SEVERE (ADR-0042).
     */
    private Library loadInitial(Compiler compiler, Path contentRoot) {
        try {
            return LibraryLoader.load(contentRoot, compiler, 0);
        } catch (Throwable failure) {
            plugin.getLogger().log(Level.SEVERE, "content load failed; enabling with no content", failure);
            Diagnostics diagnostics = new Diagnostics();
            diagnostics.error(DiagCode.E_LOAD_IO, "content load failed: " + failure, Source.ofFile("content"));
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
        // When a pack owns the config surface, the bundled defaults must NOT be re-laid over it.
        if (pack.PackStore.activePack(plugin.getDataFolder().toPath()).isPresent()) {
            return;
        }
        saveDefaultFile("config.yml");
        saveDefaultFile("lang.yml");
        saveDefaultTree("content");
        saveDefaultTree("items");
        saveDefaultTree("menus");
    }

    private void saveDefaultFile(String name) {
        if (Files.exists(plugin.getDataFolder().toPath().resolve(name))) {
            return;
        }
        try {
            plugin.saveResource(name, false);
        } catch (RuntimeException missing) {
            plugin.getLogger().log(Level.WARNING, "could not save default '" + name + "'", missing);
        }
    }

    /** Extract one bundled tree, driven by its {@code <root>/index.txt} manifest. */
    private void saveDefaultTree(String root) {
        Path dataFolder = plugin.getDataFolder().toPath();
        for (String relative : shippedPaths(root)) {
            String resource = root + "/" + relative;
            if (Files.exists(dataFolder.resolve(resource))) {
                continue;
            }
            try {
                plugin.saveResource(resource, false);
            } catch (RuntimeException missing) {
                plugin.getLogger().log(Level.WARNING, "could not save default '" + resource + "'", missing);
            }
        }
    }

    private List<String> shippedPaths(String root) {
        InputStream in = plugin.getResource(root + "/index.txt");
        if (in == null) {
            plugin.getLogger().warning(root + "/index.txt missing from the jar — no defaults extracted for " + root);
            return List.of();
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return reader.lines().map(String::trim).filter(line -> !line.isEmpty() && !line.startsWith("#")).toList();
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "could not read " + root + "/index.txt", e);
            return List.of();
        }
    }

    private void logItems(compile.load.ItemsConfig config) {
        long errors = config.diagnostics().stream().filter(Diagnostic::blocking).count();
        plugin.getLogger().info("items config loaded: soul-gem=" + config.soulGem().isPresent()
                + ", crystal=" + config.crystal().isPresent()
                + ", heroic=" + config.heroic().isPresent()
                + ", slots=" + config.slots().isPresent()
                + ", scrolls=" + config.scrolls().isPresent()
                + ", unopened-book=" + config.unopenedBook().isPresent()
                + ", " + config.diagnostics().size() + " diagnostic(s), " + errors + " error(s)");
        for (Diagnostic diagnostic : config.diagnostics()) {
            plugin.getLogger().warning("  " + diagnostic);
        }
    }

    private void logMaster(MasterConfig config) {
        plugin.getLogger().info("config.yml loaded: slots.base=" + config.slots().base()
                + ", crystals.slots=" + config.crystals().slots()
                + ", integrations[protection=" + config.integrations().protection()
                + ", economy=" + config.integrations().economy() + "]"
                + ", reload.auto-seconds=" + config.reload().autoSeconds()
                + ", " + config.diagnostics().size() + " diagnostic(s)");
        for (Diagnostic diagnostic : config.diagnostics()) {
            plugin.getLogger().warning("  " + diagnostic);
        }
    }

    private void logLoad(Library library) {
        long errors = library.diagnostics().stream().filter(Diagnostic::blocking).count();
        plugin.getLogger().info("content loaded: " + library.snapshot().abilityCount() + " abilities, "
                + library.diagnostics().size() + " diagnostic(s), " + errors + " error(s)");
        for (Diagnostic diagnostic : library.diagnostics()) {
            plugin.getLogger().warning("  " + diagnostic);
        }
    }
}
