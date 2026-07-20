package bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import bootstrap.wire.FeatureModule;
import bootstrap.wire.ModuleFold;
import bootstrap.wire.Modules;
import bootstrap.wire.Toggle;
import bootstrap.wire.Wire;
import compile.load.MasterConfig;
import compile.load.MasterConfigHolder;
import engine.stores.EngineStores;
import engine.stores.PlayerScoped;
import engine.stores.SoulModeStore;
import engine.trigger.BuiltinTriggers;
import feature.menu.MintCatalog;
import feature.menu.Mintable;
import item.mint.VanillaEnchants;
import java.lang.reflect.RecordComponent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.event.Listener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import platform.item.ItemGroups;

/**
 * The semantic golden gate over the REAL {@link Modules} registry (ADR-0047 §8.2): the strongest lock. Builds the
 * real 20-module registry against test doubles ({@code mock(BootCore)} + inert era listeners) and asserts the
 * orders and sets the fold materializes — the golden listener sequence (with the two proven-disjoint moves), the
 * onDisable stop order, the menu order, the derived mint sets/tiles, toggle-key validity, and the
 * COMMANDS&harr;self-mint drift lock. Every feature-service ctor is pure field assignment, so the registry
 * constructs without a live server.
 */
class RegistryWiringTest {

    // ── inert era listeners: the seam-provided listeners get STABLE names for the golden ────────────
    static final class ArmourFeeder implements Listener { }

    static final class HandChangeFeeder implements Listener { }

    static final class PotionLockGuard implements Listener { }

    static final class FreezeDamageGuard implements Listener { }

    static final class ItemDamageSource implements Listener { }

    static final class HeroicDurabilitySave implements Listener { }

    static final class MaskBreakSource implements Listener { }

    static final class StationGuard implements Listener { }

    static final class DispenseArmorGuard implements Listener { }

    /** The golden global listener registration sequence, all toggles on (§2 with EngineStoreListener after Immune,
     *  heroic-durability right after Heroic). Fold Events + the materialized guard/sweep, in registry order. */
    private static final List<String> GOLDEN_LISTENERS = List.of(
            "CombatListener", "RageStacksListener", "ComboDotSyncListener", "EquipListener", "ArmourFeeder", "HandChangeFeeder",
            "SoulListener", "SoulInteractListener", "SoulInventoryListener",
            "TriggerListeners", "PlacedBlockTracker", "ItemDamageSource", "FallingBlockListener",
            "GuardianHurtListener", "SummonTargetGuardListener", // ADR-0071 amendments: never target your summoner
            "TempEquipListener", "TimedRevertListener", "TempBlockGuardListener",
            "HellfireFloorListener", "KeepOnDeathListener",
            "TeleblockListener", "ImmuneListener", "PotionLockGuard", "FreezeDamageGuard",
            "EngineStoreListener", "VanillaGuardListener", "HeadEquipGuard", "DispenseArmorGuard", "StationGuard",
            "CarrierListener", "CrystalListener",
            "HeroicListener", "HeroicDurabilitySave",
            "SlotListener", "UnopenedBookListener", "UseItemListener", "UseItemConsumeListener",
            "PetUseListener", "PetLevelListener", "PetFoodListener", "PetHotbarListener", "PetSummonListener",
            "IllusionCanonGuard", "MaskBreakSource", "MaskListener", "MaskRemoveListener", "MaskIllusionListener", "MobTargetGuard", "InvseeGuard",
            "NearGuard", "SplashHealGuard",
            "ReforgeListener", "ReforgeUseListener", "ReforgeStrikeListener", "ReforgeTempoGuardListener", // ADR-0070/0071
            "ScrollListener", "HolyScrollListener", "NametagListener", "TrakListener", "ShotWeapons",
            "MenuListener", "GodlyTransmogListener");

    private static final List<String> GOLDEN_STOPS = List.of(
            "REPEATING tasks", "HELD/PASSIVE buffs", "maintained passives",   // equip
            "soul aura task",                                                 // souls
            "frozen windows",                                                 // controls (ADR-0065)
            "falling-block casts", "guardian casts", "combat tags", "damage marks", "owner zones", "temp equips", // stores
            "pet summon registry", "bat swarms", "bat cloud targets", "pet armed windows", "pet shared-use gate", "pet home windows", "pet home visuals", // pets (0052/0059/0060/0061/0068/0070)
            "mask illusions", "mask provocations",                            // masks (ADR-0053)
            "gravity wells", "castling channels", "javelin flights", "control locks", // reforges (ADR-0070/0071 Plan B)
            "bStats");                                                        // coreStops

    private static final List<String> GOLDEN_MENUS = List.of("hub", "console", "mint", "apply", "enchants", "sets",
            "crystals", "pets", "masks", "reforges", "reference", "transmog", "enchanter", "alchemist", "tinkerer",
            "admin");

    private static final Set<String> GOLDEN_GIVE_KEYS = Set.of("gem", "dust", "whitescroll", "book", "unopened",
            "useitem", "use-item", "crystal", "extractor", "heroic", "upgrade", "orb", "blackscroll", "randomizer",
            "transmog", "godlytransmog", "holy", "nametag", "blocktrak", "mobtrak", "soultrak", "fishtrak", "set",
            "pet", "pets", "petfood", "pet-food", "mask", "masks", "reforge", "reforges");

    private static final Set<String> GOLDEN_SELF_MINTS = Set.of("gem", "crystal", "heroic", "orb", "book",
            "blackscroll", "randomizer", "transmog", "godlytransmog", "holy", "nametag", "dust", "whitescroll",
            "unopened", "reforge");

    private static final List<String> GOLDEN_TILE_LABELS = List.of("soul gem", "slot expander", "heroic upgrade",
            "item extractor", "black scroll", "randomizer scroll", "transmog scroll", "godly transmog",
            "holy white scroll", "item nametag", "success dust", "white scroll",
            "blocktrak gem", "mobtrak gem", "soultrak gem", "fishtrak gem", "pet food");

    private Modules modules;
    private List<Mintable> mintables;
    private compile.load.ContentHolder content;

    @BeforeEach
    void buildRealRegistry(@TempDir Path dataDir) {
        bootstrap.compat.EraServices bindings = mock(bootstrap.compat.EraServices.class);
        when(bindings.hands()).thenReturn(mock(feature.compat.Hands.class));
        when(bindings.sounds()).thenReturn(mock(feature.compat.Sounds.class));
        when(bindings.particleFx()).thenReturn(mock(feature.fx.ParticleFx.class));
        when(bindings.vanillaStats()).thenReturn(mock(feature.heroic.VanillaStats.class));
        when(bindings.anvilRename()).thenReturn(mock(feature.scroll.AnvilRename.class));
        when(bindings.texturedHeads()).thenReturn(item.head.TexturedHeads.NONE);
        when(bindings.equipmentRepaint()).thenReturn(item.head.EquipmentRepaint.NONE); // ADR-0053 inert repaint
        when(bindings.headEquip()).thenReturn(item.head.HeadEquip.NONE); // 1.8.4 inert client helmet-wearability strip
        when(bindings.headAttributes()).thenReturn(item.head.HeadAttributes.NONE); // 1.8.1 worn-slot dressing
        when(bindings.itemBytes()).thenReturn(item.head.ItemBytes.NONE); // ADR-0064 illusion payload codec
        when(bindings.actorProbe()).thenReturn(mock(engine.run.ActorProbe.class)); // 1.8.1 cage pre-check isAir
        when(bindings.armourChangeFeeder(any())).thenReturn(new ArmourFeeder());
        when(bindings.handChangeFeeder(any())).thenReturn(new HandChangeFeeder());
        when(bindings.potionLockGuard()).thenReturn(new PotionLockGuard());
        when(bindings.freezeDamageGuard()).thenReturn(new FreezeDamageGuard());
        when(bindings.itemDamageSource(any())).thenReturn(new ItemDamageSource());
        when(bindings.heroicDurabilitySave(any(), any())).thenReturn(new HeroicDurabilitySave());
        when(bindings.maskBreakSource(any(), any())).thenReturn(new MaskBreakSource());
        when(bindings.stationGuard(any())).thenReturn(new StationGuard());
        when(bindings.dispenseArmorGuard(any())).thenReturn(new DispenseArmorGuard()); // 1.8.4 dispenser head-equip guard
        when(bindings.weaponDamage()).thenReturn(feature.reforge.WeaponDamage.NONE); // ADR-0071 Javelin swing seam (JavelinService ctor)

        org.bukkit.plugin.java.JavaPlugin plugin = mock(org.bukkit.plugin.java.JavaPlugin.class);
        org.bukkit.Server server = mock(org.bukkit.Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getServicesManager()).thenReturn(mock(org.bukkit.plugin.ServicesManager.class));
        when(plugin.getDataFolder()).thenReturn(dataDir.toFile());

        this.content = mock(compile.load.ContentHolder.class);
        compile.load.Library library = mock(compile.load.Library.class);
        when(content.library()).thenReturn(library);
        when(library.tiers()).thenReturn(mock(compile.load.TierRegistry.class));
        when(library.tiers().tiers()).thenReturn(List.of()); // no rarity tiers → unopened contributes no tiles
        when(library.pets()).thenReturn(List.of()); // no pet defs → the per-pet tiles are empty
        when(library.masks()).thenReturn(List.of()); // no mask defs → the per-mask tiles are empty (ADR-0053)
        when(library.reforges()).thenReturn(List.of()); // no reforge defs → the per-reforge tiles are empty (ADR-0070)

        bootstrap.wire.BootCore core = mock(bootstrap.wire.BootCore.class);
        when(core.plugin()).thenReturn(plugin);
        when(core.bindings()).thenReturn(bindings);
        when(core.caps()).thenReturn(mock(platform.caps.Capabilities.class));
        when(core.tick()).thenReturn(new AtomicLong());
        when(core.particleFx()).thenReturn(mock(feature.fx.ParticleFx.class));
        when(core.resolvers()).thenReturn(new platform.resolve.RegistryResolvers()); // ADR-0061 home visuals colour/particle resolve
        when(core.store()).thenReturn(mock(item.codec.ItemStateStore.class));
        when(core.hands()).thenReturn(mock(feature.compat.Hands.class));
        when(core.projectiles()).thenReturn(mock(feature.compat.Projectiles.class));
        when(core.sounds()).thenReturn(mock(feature.compat.Sounds.class));
        when(core.anvilRename()).thenReturn(mock(feature.scroll.AnvilRename.class));
        when(core.addonKinds()).thenReturn(new CopyOnWriteArrayList<>());
        when(core.effectRegistry()).thenReturn(() -> null);
        when(core.compilerFactory()).thenReturn(() -> null);
        when(core.content()).thenReturn(content);
        when(core.items()).thenReturn(mock(compile.load.ItemsHolder.class));
        when(core.master()).thenReturn(new MasterConfigHolder(MasterConfig.defaults()));
        when(core.messages()).thenReturn(mock(platform.lang.Messages.class));
        when(core.menusHolder()).thenReturn(mock(compile.load.MenusHolder.class));
        when(core.contentRoot()).thenReturn(dataDir.resolve("content"));
        when(core.codec()).thenReturn(mock(item.codec.CombatCodec.class));
        when(core.itemViews()).thenReturn(mock(item.view.ItemViewCache.class));
        when(core.worn()).thenReturn(mock(item.worn.WornStateStore.class));
        when(core.appliedSlot()).thenReturn(mock(item.codec.AppliedSlot.class));
        when(core.carrierCodec()).thenReturn(mock(item.codec.CarrierCodec.class));
        when(core.trakCodec()).thenReturn(mock(item.codec.TrakCodec.class));
        when(core.petCodec()).thenReturn(new item.codec.PetCodec(item.codec.ItemKeys.of(),
                mock(item.codec.ItemStateStore.class)));
        when(core.petArmedStore()).thenReturn(new feature.pet.PetArmedStore());
        when(core.maskCodec()).thenReturn(new item.codec.MaskCodec(item.codec.ItemKeys.of(),
                mock(item.codec.ItemStateStore.class))); // ADR-0053 mask-item identity codec
        when(core.reforgeCodec()).thenReturn(new item.codec.ReforgeCodec(item.codec.ItemKeys.of(),
                mock(item.codec.ItemStateStore.class))); // ADR-0070 reforge-item identity codec
        when(core.lore()).thenReturn(mock(item.render.LoreRenderer.class));
        when(core.itemGroups()).thenReturn(ItemGroups.standard());
        when(core.recompose()).thenReturn(stack -> { });
        when(core.rolls()).thenReturn(new java.util.Random());
        when(core.vanillaEnchants()).thenReturn(VanillaEnchants.NONE);
        when(core.enchanter()).thenReturn(mock(feature.apply.ItemEnchanter.class));
        when(core.soulModes()).thenReturn(new SoulModeStore());
        when(core.soulService()).thenReturn(mock(feature.soul.SoulService.class));
        when(core.stores()).thenReturn(EngineStores.fresh());
        when(core.sinkEnv()).thenReturn(engine.sink.SinkEnv.of(platform.economy.EconomyService.NONE,
                engine.sink.SoulDebit.NONE, EngineStores.fresh(), new AtomicLong()::get));
        when(core.executor()).thenReturn(mock(engine.run.AbilityExecutor.class));
        when(core.dispatch()).thenReturn(mock(feature.combat.CombatDispatch.class));
        when(core.triggerDispatch()).thenReturn(mock(feature.trigger.TriggerDispatch.class));
        when(core.triggers()).thenReturn(BuiltinTriggers.registry());
        when(core.coreStops()).thenReturn(List.of(new FeatureModule.Stop("bStats", () -> { })));

        this.modules = new Modules(core, () -> ModuleFold.Report.EMPTY);
        this.mintables = new ArrayList<>();
        for (FeatureModule module : modules.registry()) {
            this.mintables.addAll(module.mintables());
        }
    }

    // ── golden listener order ────────────────────────────────────────────────────────────────────

    /** The wire pass sequence the fold would record: Events → simple name, guard/sweep → their materialized names,
     *  Install skipped. {@code offBootKeys} drops a BOOT module's wires (the fold's gating), computed here without
     *  re-constructing the registry. */
    private List<String> listenerOrder(Set<String> offBootKeys) {
        List<String> out = new ArrayList<>();
        for (FeatureModule module : modules.registry()) {
            Toggle toggle = module.toggle();
            if (toggle.gatesBoot() && offBootKeys.contains(toggle.key())) {
                continue;
            }
            for (Wire wire : module.wires()) {
                if (wire instanceof Wire.Events events) {
                    out.add(events.listener().getClass().getSimpleName());
                } else if (wire instanceof Wire.PluginItemGuard) {
                    out.add("VanillaGuardListener");
                } else if (wire instanceof Wire.QuitSweep) {
                    out.add("EngineStoreListener");
                }
            }
        }
        return out;
    }

    @Test
    void listenerRegistrationOrderMatchesTheGoldenSequence() {
        assertEquals(GOLDEN_LISTENERS, listenerOrder(Set.of()));
    }

    @Test
    void aBootToggleOffDropsExactlyThatModulesWires() {
        // souls off drops its three listeners; nothing else moves (the two moves are structural, not toggle-driven).
        List<String> soulsOff = listenerOrder(Set.of("features.souls"));
        assertTrue(GOLDEN_LISTENERS.containsAll(soulsOff));
        assertEquals(GOLDEN_LISTENERS.size() - 3, soulsOff.size());
        assertTrue(soulsOff.stream().noneMatch(n -> n.startsWith("Soul")));
        // scrolls off drops the scroll block (Scroll/Holy/Nametag) AND the traks listener (same boot key) AND the
        // godly-transmog gesture is not added — but that is a construction-time condition (menus module), so it is
        // asserted here only for the three scroll listeners + trak the fold gates.
        List<String> scrollsOff = listenerOrder(Set.of("features.scrolls"));
        assertTrue(scrollsOff.stream().noneMatch(n ->
                n.equals("ScrollListener") || n.equals("HolyScrollListener") || n.equals("NametagListener")
                        || n.equals("TrakListener")));
    }

    // ── onDisable stop order ─────────────────────────────────────────────────────────────────────

    @Test
    void disableStopOrderMatchesTheGolden() {
        List<String> stops = new ArrayList<>();
        for (FeatureModule module : modules.registry()) {
            for (FeatureModule.Stop step : module.stops()) {
                stops.add(step.what());
            }
        }
        stops.add("bStats"); // coreStops runs after the module stops (StarEnchantsPlugin passes core.coreStops())
        assertEquals(GOLDEN_STOPS, stops);
    }

    // ── menu order ───────────────────────────────────────────────────────────────────────────────

    @Test
    void menuRegistrationOrderMatchesTheGolden() {
        List<FeatureModule.MenuEntry> menuEntries = new ArrayList<>();
        for (FeatureModule module : modules.registry()) {
            menuEntries.addAll(module.menus());
        }
        menuEntries.sort(java.util.Comparator.comparingInt(FeatureModule.MenuEntry::rank));
        List<String> names = menuEntries.stream().map(e -> e.menu().name()).toList();
        assertEquals(GOLDEN_MENUS, names);
    }

    // ── mint sets + tiles ────────────────────────────────────────────────────────────────────────

    @Test
    void giveKeysAreTheGoldenSetAndSelfMintsMatchTheirCommandRows() {
        Set<String> giveKeys = new LinkedHashSet<>();
        Set<String> selfMints = new LinkedHashSet<>();
        for (Mintable mintable : mintables) {
            giveKeys.add(mintable.type());
            giveKeys.addAll(mintable.aliases());
            if (mintable.self() != null) {
                selfMints.add(mintable.type());
            }
        }
        assertEquals(GOLDEN_GIVE_KEYS, giveKeys);
        assertEquals(GOLDEN_SELF_MINTS, selfMints);
        // Drift lock (§7): every self-mint type has a same-named /se <type> COMMANDS row, and no self-mint lacks one.
        for (String type : selfMints) {
            assertTrue(SeCommand.SUBCOMMANDS.contains(type),
                    "self-mint '" + type + "' has no COMMANDS row — the /se <type> row and the mintable drifted apart");
        }
    }

    @Test
    void mintTileOrderMatchesTheShippedCuratedOrder() {
        MintCatalog catalog = new MintCatalog(mintables, content);
        List<String> labels = catalog.entries().stream().map(MintCatalog.Entry::label).toList();
        assertEquals(GOLDEN_TILE_LABELS, labels); // tiers stubbed empty → the 16 fixed tiles, rank-sorted
    }

    // ── toggle-key validity + module uniqueness ─────────────────────────────────────────────────

    @Test
    void everyToggleKeyIsAFeaturesSectionComponentAndModuleNamesAreUnique() {
        Set<String> featureFields = new java.util.HashSet<>();
        for (RecordComponent component : MasterConfig.FeaturesSection.class.getRecordComponents()) {
            featureFields.add(component.getName());
        }
        Set<String> names = new java.util.HashSet<>();
        for (FeatureModule module : modules.registry()) {
            assertTrue(names.add(module.name()), "duplicate module name: " + module.name());
            Toggle toggle = module.toggle();
            if (!toggle.key().isEmpty()) {
                assertTrue(toggle.key().startsWith("features."), "toggle key not under features.: " + toggle.key());
                // Toggle keys carry the kebab CONFIG path (features.use-items) an operator edits; the record
                // component is the camelCase Java name (useItems). Normalise before matching.
                String field = camel(toggle.key().substring("features.".length()));
                assertTrue(featureFields.contains(field),
                        "toggle key '" + toggle.key() + "' is not a FeaturesSection component");
            }
            // A BOOT gate must carry a disabled log or be the documented traks exception (shares scrolls' one log).
            if (toggle.gatesBoot()) {
                assertTrue(!toggle.disabledLog().isEmpty() || module.name().equals("traks"),
                        "BOOT module '" + module.name() + "' has neither a disabled log nor the traks exemption");
            }
        }
    }

    private static String camel(String kebab) {
        StringBuilder out = new StringBuilder(kebab.length());
        boolean up = false;
        for (int i = 0; i < kebab.length(); i++) {
            char c = kebab.charAt(i);
            if (c == '-') {
                up = true;
            } else {
                out.append(up ? Character.toUpperCase(c) : c);
                up = false;
            }
        }
        return out.toString();
    }

    // ── quit sweep declared stores ───────────────────────────────────────────────────────────────

    @Test
    void quitSweepGathersEveryDeclaredPlayerStore() {
        List<PlayerScoped> declared = new ArrayList<>();
        for (FeatureModule module : modules.registry()) {
            declared.addAll(module.playerStores());
        }
        // souls (soul-total cache) + pets (armed windows + shared 2s use gate (ADR-0070) + dig homes + home
        // visuals + sweep fingerprints + bat-cloud publisher (ADR-0068), ADR-0052/0061) + masks (illusion cache
        // + provocations, ADR-0053) + scrolls (kept items + nametag captures) + combat (combo-DoT park ledger,
        // ADR-0069) + reforges (gravity-well + castling + javelin machine scopes, ADR-0071 Plan B)
        // = 15 module-owned stores swept on quit.
        assertEquals(15, declared.size());
    }
}
