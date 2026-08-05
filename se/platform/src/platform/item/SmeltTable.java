package platform.item;

import java.util.EnumMap;
import java.util.Map;
import org.bukkit.Material;

/**
 * The block → smelted-product table shared by BOTH drop-transform paths: the MINE-scoped {@code SMELT}
 * read-back ({@code feature.combat.MineDrops}) and {@code BREAK_BLOCK}'s per-block {@code smelt} on a
 * resolved volume (the sink). It lives here rather than on either caller because the two paths are disjoint
 * — one takes a {@code BlockBreakEvent}, the other a location on a region thread — and a second copy of the
 * table would let a furnace recipe drift between "the block you mined" and "the block the blast took".
 *
 * <p>Built by NAME so a product absent on the running version (netherite scrap, the copper/deepslate ores)
 * is simply missing from the map instead of failing to link on the 1.8 floor.
 */
public final class SmeltTable {

    private static final Map<Material, Material> PRODUCTS = build();

    private SmeltTable() {
    }

    /** The smelted product of {@code block}, or {@code null} when the block does not smelt on this version. */
    public static Material productOf(Material block) {
        return block == null ? null : PRODUCTS.get(block);
    }

    private static Map<Material, Material> build() {
        Map<Material, Material> map = new EnumMap<>(Material.class);
        put(map, "IRON_ORE", "IRON_INGOT");
        put(map, "DEEPSLATE_IRON_ORE", "IRON_INGOT");
        put(map, "GOLD_ORE", "GOLD_INGOT");
        put(map, "DEEPSLATE_GOLD_ORE", "GOLD_INGOT");
        put(map, "NETHER_GOLD_ORE", "GOLD_INGOT");
        put(map, "COPPER_ORE", "COPPER_INGOT");
        put(map, "DEEPSLATE_COPPER_ORE", "COPPER_INGOT");
        put(map, "ANCIENT_DEBRIS", "NETHERITE_SCRAP");
        put(map, "SAND", "GLASS");
        put(map, "RED_SAND", "GLASS");
        put(map, "COBBLESTONE", "STONE");
        put(map, "STONE", "STONE");
        put(map, "NETHERRACK", "NETHER_BRICK");
        put(map, "CLAY_BALL", "BRICK");
        return map;
    }

    private static void put(Map<Material, Material> map, String block, String product) {
        Material from = Material.getMaterial(block);
        Material to = Material.getMaterial(product);
        if (from != null && to != null) {
            map.put(from, to);
        }
    }
}
