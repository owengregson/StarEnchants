package tester.suite;

import compile.load.ContentHolder;
import compile.load.CrystalConfig;
import compile.load.Library;
import compile.load.LibraryLoader;
import compile.load.SlotConfig;
import engine.boot.ContentCompiler;
import feature.apply.ApplyGestureListener;
import feature.apply.ItemEnchanter;
import feature.crystal.CrystalListener;
import feature.crystal.CrystalService;
import feature.slot.SlotListener;
import feature.slot.SlotService;
import item.codec.CombatCodec;
import item.codec.CrystalExtractorCodec;
import item.codec.CrystalItemCodec;
import item.codec.ItemKeys;
import item.codec.SlotItemCodec;
import item.render.LoreRenderer;
import item.render.LoreStyle;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import platform.item.ItemGroups;
import platform.lang.Messages;
import platform.sched.Scheduling;
import tester.fake.FakePlayers;
import tester.harness.Harness;

/**
 * Live proof of the shared apply-gesture base (ADR-0041): a real {@link InventoryClickEvent} over an opened
 * view is committed (crystal apply, slot orb) or ignored (top-inventory, shift-click) exactly as the guard
 * preamble + commit protocol specify. Dispatched directly to {@link ApplyGestureListener#onGestureClick} (no
 * {@code callEvent}) so the production listeners never cross-talk.
 */
@SuppressWarnings("deprecation") // getCursor/setCursor/setItem: the floor-stable view path the base itself uses
public final class ApplyGestureSuite implements Harness.Scenario {

    private static final String SPARK = """
            display: Spark
            applies-to: [ALL]
            trigger: ATTACK
            chance: 100
            effects: [{ MODIFY_HEALTH: { amount: 1 } }]
            """;

    private static final String CRYSTAL_KEY = "crystals/spark";

    private final Plugin plugin;

    public ApplyGestureSuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        h.expect("gesture.crystal.applyCommits");
        h.expect("gesture.crystal.topInventoryIgnored");
        h.expect("gesture.slot.orbAppliesAndConsumes");
        h.expect("gesture.shiftClickIgnored");

        CombatCodec combat;
        CrystalService crystals;
        CrystalListener crystalLeaf;
        SlotService slots;
        SlotItemCodec slotCodec;
        SlotListener slotLeaf;
        try {
            Path root = Files.createTempDirectory("se-apply-gesture-suite");
            write(root, "crystals/spark.yml", SPARK);
            Library library = LibraryLoader.load(root, ContentCompiler.production(), 0);
            if (library.hasErrors()) {
                failAll(h, "content failed to compile: " + library.diagnostics());
                return;
            }
            ContentHolder content = new ContentHolder(library);
            ItemKeys keys = ItemKeys.of();
            combat = new CombatCodec(keys.combat(), Stores.state());
            LoreRenderer lore = Stores.lore(
                    LoreRenderer.Config.of(LoreStyle.DEFAULT, key -> content.library().displayNameOf(key)));
            ItemEnchanter enchanter = new ItemEnchanter(combat, lore, content, ItemGroups.standard(),
                    () -> ItemEnchanter.DEFAULT_BASE_SLOTS, () -> ItemEnchanter.DEFAULT_CRYSTAL_SLOTS,
                    () -> ItemEnchanter.DEFAULT_MAX_MERGE, platform.lang.Messages.defaults(), item.mint.VanillaEnchants.NONE);
            crystals = new CrystalService(new CrystalItemCodec(keys.crystalItem(), Stores.state()),
                    new CrystalExtractorCodec(keys.crystalExtractor(), Stores.state()), enchanter, content,
                    CrystalConfig::defaults, () -> 4, Messages.defaults());
            crystalLeaf = new CrystalListener(crystals, Messages.defaults(), Stores.sounds());
            slotCodec = new SlotItemCodec(keys.slotItem(), keys.slotSuccess(), Stores.state());
            SlotConfig slotCfg = new SlotConfig("ENDER_EYE", "&5Orb", List.of(), 3,
                    ItemEnchanter.DEFAULT_BASE_SLOTS + 5, 100, 100, List.of("ALL"));
            slots = new SlotService(slotCodec, combat, lore, () -> slotCfg,
                    () -> ItemEnchanter.DEFAULT_BASE_SLOTS, platform.lang.Messages.defaults(),
                    ItemGroups.standard(), new java.util.Random());
            slotLeaf = new SlotListener(slots, Messages.defaults(), Stores.sounds());
        } catch (IOException e) {
            failAll(h, e.toString());
            return;
        }

        World world = plugin.getServer().getWorlds().get(0);
        Location at = world.getSpawnLocation();
        int cx = at.getBlockX() >> 4;
        int cz = at.getBlockZ() >> 4;

        Scheduling.onGlobal(() -> {
            world.setChunkForceLoaded(cx, cz, true);
            Scheduling.onRegion(at, () -> {
                Player player;
                try {
                    player = FakePlayers.spawn(world, "se_gesture_p");
                } catch (Throwable t) {
                    failAll(h, "spawn: " + t);
                    return;
                }
                Scheduling.onEntity(player, () -> {
                    try {
                        runChecks(h, player, combat, crystals, crystalLeaf, slots, slotCodec, slotLeaf);
                    } finally {
                        player.closeInventory();
                        FakePlayers.despawn(player);
                    }
                });
            });
        });
    }

    private void runChecks(Harness h, Player player, CombatCodec combat, CrystalService crystals,
                           CrystalListener crystalLeaf, SlotService slots, SlotItemCodec slotCodec,
                           SlotListener slotLeaf) {
        h.guard("gesture.crystal.applyCommits", () -> {
            InventoryView view = openChest(player);
            int raw = view.getTopInventory().getSize(); // first bottom (player-inventory) raw slot
            ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
            view.setItem(raw, sword);
            view.setCursor(crystals.mint(List.of(CRYSTAL_KEY)));
            InventoryClickEvent event = click(view, raw, ClickType.LEFT, InventoryAction.SWAP_WITH_CURSOR);
            crystalLeaf.onGestureClick(event);

            if (!event.isCancelled()) {
                throw new IllegalStateException("the apply click was not cancelled");
            }
            ItemStack after = view.getItem(raw);
            if (after == null || !combat.read(after).crystals().contains(CRYSTAL_KEY)) {
                throw new IllegalStateException("the sword did not gain the crystal entry");
            }
            if (notEmpty(view.getCursor())) {
                throw new IllegalStateException("the crystal on the cursor was not consumed");
            }
            // Lore is rendered from state (ADR-0040): the recompose must have written a non-empty lore block.
            if (after.getItemMeta() == null || after.getItemMeta().getLore() == null
                    || after.getItemMeta().getLore().isEmpty()) {
                throw new IllegalStateException("the applied crystal did not recompose the gear lore");
            }
        });

        h.guard("gesture.crystal.topInventoryIgnored", () -> {
            InventoryView view = openChest(player);
            ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
            view.setItem(0, sword); // a TOP-inventory slot
            view.setCursor(crystals.mint(List.of(CRYSTAL_KEY)));
            InventoryClickEvent event = click(view, 0, ClickType.LEFT, InventoryAction.SWAP_WITH_CURSOR);
            crystalLeaf.onGestureClick(event);

            if (event.isCancelled()) {
                throw new IllegalStateException("a top-inventory click was claimed");
            }
            if (!combat.read(view.getItem(0)).crystals().isEmpty()) {
                throw new IllegalStateException("the top-inventory sword was mutated");
            }
        });

        h.guard("gesture.slot.orbAppliesAndConsumes", () -> {
            InventoryView view = openChest(player);
            int raw = view.getTopInventory().getSize();
            ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
            view.setItem(raw, sword);
            ItemStack orb = slots.mintOrb(100);
            int grant = slotCodec.amountOf(orb);
            view.setCursor(orb);
            InventoryClickEvent event = click(view, raw, ClickType.LEFT, InventoryAction.SWAP_WITH_CURSOR);
            slotLeaf.onGestureClick(event);

            if (!event.isCancelled()) {
                throw new IllegalStateException("the orb apply click was not cancelled");
            }
            ItemStack after = view.getItem(raw);
            if (after == null || combat.read(after).added() != grant) {
                throw new IllegalStateException("the orb did not add " + grant + " slots: "
                        + (after == null ? "null" : combat.read(after).added()));
            }
            if (notEmpty(view.getCursor())) {
                throw new IllegalStateException("the orb on the cursor was not consumed");
            }
        });

        h.guard("gesture.shiftClickIgnored", () -> {
            InventoryView view = openChest(player);
            int raw = view.getTopInventory().getSize();
            view.setItem(raw, new ItemStack(Material.DIAMOND_SWORD));
            view.setCursor(crystals.mint(List.of(CRYSTAL_KEY)));
            InventoryClickEvent event = click(view, raw, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
            crystalLeaf.onGestureClick(event);
            if (event.isCancelled()) {
                throw new IllegalStateException("a shift-click was claimed by the gesture");
            }
        });
    }

    private InventoryView openChest(Player player) {
        Inventory chest = plugin.getServer().createInventory(null, 27);
        return player.openInventory(chest);
    }

    private InventoryClickEvent click(InventoryView view, int rawSlot, ClickType type, InventoryAction action) {
        return new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, rawSlot, type, action);
    }

    private static boolean notEmpty(ItemStack stack) {
        return stack != null && stack.getType() != Material.AIR;
    }

    private static void failAll(Harness h, String reason) {
        h.fail("gesture.crystal.applyCommits", reason);
        h.fail("gesture.crystal.topInventoryIgnored", reason);
        h.fail("gesture.slot.orbAppliesAndConsumes", reason);
        h.fail("gesture.shiftClickIgnored", reason);
    }

    private static void write(Path root, String relative, String yaml) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, yaml, StandardCharsets.UTF_8);
    }
}
