package tester.suite;

import feature.guard.IllusionCanonGuard;
import feature.guard.StationGuardRules;
import feature.mask.MaskIllusionService;
import feature.mask.MaskIllusionStore;
import item.codec.CombatCodec;
import item.codec.CombatState;
import item.codec.HeroicStat;
import item.codec.ItemKeys;
import item.head.EquipmentRepaint;
import item.head.HeadAttributes;
import item.head.IllusionMark;
import item.head.ModernItemBytes;
import item.head.TexturedHeads;
import item.view.ItemViewCache;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import platform.sched.Scheduling;
import tester.harness.CombatRig;
import tester.harness.Harness;

/**
 * The illusion canonical seam, live (ADR-0064): a dressed mask head must never survive contact with a real
 * inventory. Wires the production {@link IllusionCanonGuard} + {@link IllusionMark} exactly as the composition
 * root does (the MaskBreakSuite own-wiring pattern). Asserted on server-side state only: the byte payload
 * round-trips on THIS version, the creative echo is denied, a leaked head undresses on click and on refresh,
 * and a creative wearer's self-view repaint carries the TRUE helmet.
 */
public final class MaskCanonSuite implements Harness.Scenario {

    private static final String MASK_KEY = "masks/blaze";
    private static final int HELMET_RAW_SLOT = 5;  // own-view crafting layout: 0 result, 1-4 grid, 5 helmet
    private static final int STORAGE_SLOT = 9;     // first storage cell; raw slot == inventory index in own view

    private final Plugin plugin;

    public MaskCanonSuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        h.expect("maskcanon.payloadRoundTrip");
        h.expect("maskcanon.creativeEchoDenied");
        h.expect("maskcanon.clickUndressesLeakedHead");
        h.expect("maskcanon.refreshRepairsWornLeak");
        h.expect("maskcanon.creativeWearerSelfSeesTruth");
        h.expect("maskcanon.stationGuardDeniesMaskedHelmet");

        CombatCodec codec = new CombatCodec(ItemKeys.of().combat(), Stores.state());
        IllusionMark mark = new IllusionMark(ItemKeys.of(), Stores.state(), new ModernItemBytes());

        // (1) Serialize→deserialize on the RUNNING version — the real cross-version risk — plus marker semantics.
        h.guard("maskcanon.payloadRoundTrip", () -> {
            ItemStack helmet = maskedHelmet(codec);
            ItemStack shown = new ItemStack(Material.PLAYER_HEAD);
            mark.stamp(shown, helmet);
            if (!mark.isMarked(shown)) {
                throw new IllegalStateException("stamped head not detected as an illusion");
            }
            ItemStack undressed = mark.undress(shown);
            if (undressed == null || undressed.getType() != helmet.getType()) {
                throw new IllegalStateException("undress lost the helmet: " + undressed);
            }
            CombatState state = codec.read(undressed);
            if (!MASK_KEY.equals(state.maskKey()) || state.crystals().isEmpty()) {
                throw new IllegalStateException("undress lost the combat state: " + state);
            }
            if (mark.isMarked(undressed)) {
                throw new IllegalStateException("the undressed helmet still reads as an illusion");
            }
        });

        // (6) A masked NON-set helmet is denied at the grindstone (the broadened single-sourced predicate).
        h.guard("maskcanon.stationGuardDeniesMaskedHelmet", () -> {
            java.util.function.Predicate<ItemStack> gear = StationGuardRules.pluginValueGear(codec::read);
            if (!gear.test(maskedHelmet(codec))) {
                throw new IllegalStateException("a masked non-set helmet passed the station-gear predicate");
            }
            if (gear.test(new ItemStack(Material.IRON_HELMET))) {
                throw new IllegalStateException("a plain helmet was station-guarded");
            }
        });

        World world = plugin.getServer().getWorlds().get(0);
        Location at = world.getSpawnLocation();
        int cx = at.getBlockX() >> 4;
        int cz = at.getBlockZ() >> 4;
        CombatRig rig = new CombatRig(plugin);
        rig.listen(new IllusionCanonGuard(mark)); // the production gate, wired here

        Scheduling.onGlobal(() -> {
            world.setChunkForceLoaded(cx, cz, true);
            Scheduling.onRegion(at, () -> {
                Player wearer;
                Player observer;
                try {
                    wearer = rig.spawnFake(world, "se_mc_wear");
                    observer = rig.spawnFake(world, "se_mc_watch");
                } catch (Throwable t) {
                    h.fail("maskcanon.creativeEchoDenied", "fake-player spawn: " + t);
                    h.fail("maskcanon.clickUndressesLeakedHead", "fake-player spawn: " + t);
                    h.fail("maskcanon.refreshRepairsWornLeak", "fake-player spawn: " + t);
                    h.fail("maskcanon.creativeWearerSelfSeesTruth", "fake-player spawn: " + t);
                    return;
                }

                Scheduling.onEntity(wearer, () -> {
                    // (2) Creative echo: the client "returns" the dressed head over the worn masked helmet — denied.
                    ItemStack real = maskedHelmet(codec);
                    wearer.getInventory().setHelmet(real);
                    ItemStack leaked = mark(mark, codec);
                    InventoryCreativeEvent echo = new InventoryCreativeEvent(wearer.getOpenInventory(),
                            InventoryType.SlotType.ARMOR, HELMET_RAW_SLOT, leaked);
                    plugin.getServer().getPluginManager().callEvent(echo);
                    h.guard("maskcanon.creativeEchoDenied", () -> {
                        if (!echo.isCancelled()) {
                            throw new IllegalStateException("the creative write-back of a marked head was not denied");
                        }
                        ItemStack worn = wearer.getInventory().getHelmet();
                        if (worn == null || worn.getType() == Material.PLAYER_HEAD
                                || codec.read(worn).maskKey() == null) {
                            throw new IllegalStateException("the real masked helmet did not survive the echo: " + worn);
                        }
                    });

                    // (3) A leaked head in a storage slot undresses on the first click, BEFORE any gesture runs.
                    wearer.getInventory().setItem(STORAGE_SLOT, mark(mark, codec));
                    InventoryClickEvent click = new InventoryClickEvent(wearer.getOpenInventory(),
                            InventoryType.SlotType.CONTAINER, STORAGE_SLOT, ClickType.LEFT, InventoryAction.PICKUP_ALL);
                    plugin.getServer().getPluginManager().callEvent(click);
                    h.guard("maskcanon.clickUndressesLeakedHead", () -> {
                        ItemStack slot = wearer.getInventory().getItem(STORAGE_SLOT);
                        if (slot == null || slot.getType() == Material.PLAYER_HEAD) {
                            throw new IllegalStateException("the leaked head was not undressed on click: " + slot);
                        }
                        if (codec.read(slot).maskKey() == null) {
                            throw new IllegalStateException("the undressed helmet lost its mask state");
                        }
                    });

                    // (4) A leaked head in the WORN slot is restored by the refresh-side repair.
                    RecordingRepaint recorder = new RecordingRepaint();
                    MaskIllusionService illusion = new MaskIllusionService(recorder, TexturedHeads.NONE,
                            HeadAttributes.NONE, mark, () -> null, new ItemViewCache(codec, 0), () -> true,
                            new MaskIllusionStore());
                    wearer.getInventory().setHelmet(mark(mark, codec));
                    illusion.repairWorn(wearer);
                    h.guard("maskcanon.refreshRepairsWornLeak", () -> {
                        ItemStack worn = wearer.getInventory().getHelmet();
                        if (worn == null || worn.getType() == Material.PLAYER_HEAD
                                || codec.read(worn).maskKey() == null) {
                            throw new IllegalStateException("the worn leak was not repaired: " + worn);
                        }
                    });

                    // (5) A CREATIVE wearer's self-view repaint carries the TRUE helmet; observers still get the mask.
                    MaskIllusionStore store = new MaskIllusionStore();
                    MaskIllusionService sweeper = new MaskIllusionService(recorder, TexturedHeads.NONE,
                            HeadAttributes.NONE, mark, () -> null, new ItemViewCache(codec, 0), () -> true, store);
                    wearer.getInventory().setHelmet(maskedHelmet(codec));
                    wearer.setGameMode(GameMode.CREATIVE);
                    store.put(wearer.getUniqueId(), mark(mark, codec)); // a stored illusion, as refresh would leave it
                    Scheduling.onGlobal(sweeper::sweep);

                    Scheduling.onEntityLater(wearer, 10L, () -> {
                        h.guard("maskcanon.creativeWearerSelfSeesTruth", () -> {
                            Material self = recorder.shown.get(wearer.getUniqueId());
                            Material other = recorder.shown.get(observer.getUniqueId());
                            if (self != wearer.getInventory().getHelmet().getType()) {
                                throw new IllegalStateException("creative wearer self-view was not reality: " + self);
                            }
                            if (other != Material.PLAYER_HEAD) {
                                throw new IllegalStateException("observer lost the mask illusion: " + other);
                            }
                        });
                        rig.teardown();
                    });
                });
            });
        });
    }

    /** Captures the LAST shown material per recipient — the packet payload is the assertion surface. */
    private static final class RecordingRepaint implements EquipmentRepaint {
        final Map<UUID, Material> shown = new ConcurrentHashMap<>();

        @Override public boolean helmet(Player recipient, Player wearer, ItemStack head) {
            shown.put(recipient.getUniqueId(), head.getType());
            return true;
        }
    }

    /** A DIAMOND_HELMET carrying a mask AND a crystal — the state the brick destroyed. */
    private static ItemStack maskedHelmet(CombatCodec codec) {
        ItemStack helmet = new ItemStack(Material.DIAMOND_HELMET);
        codec.write(helmet, new CombatState(Map.of(), List.of("crystals/dark"), null, null, false,
                HeroicStat.NONE, 0, MASK_KEY));
        return helmet;
    }

    /** A marked dressed head whose payload is a fresh masked helmet — the leaked visual. */
    private static ItemStack mark(IllusionMark mark, CombatCodec codec) {
        ItemStack shown = new ItemStack(Material.PLAYER_HEAD);
        mark.stamp(shown, maskedHelmet(codec));
        return shown;
    }
}
