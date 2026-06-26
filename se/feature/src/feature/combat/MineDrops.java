package feature.combat;

import feature.compat.Hands;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Applies the MINE-side {@code SMELT} (block→smelted product) and {@code TELEPORT_DROPS} (drops to breaker's
 * inventory) read-backs (Cosmic Enchants-style parity) to a {@link BlockBreakEvent}, on the firing block's
 * region thread. Either flag suppresses the vanilla drop ({@link BlockBreakEvent#setDropItems(boolean)},
 * whole 1.17.1→26.1.x range) and places the effective drops itself, so there is no duplication.
 */
public final class MineDrops {

    /** block type → its smelted product, built by name so an absent material on a given version is skipped. */
    private static final Map<Material, Material> SMELT = buildSmeltMap();

    private MineDrops() {
    }

    /** Apply the requested MINE drop transforms to {@code event}. A no-op when neither flag is set. */
    public static void apply(BlockBreakEvent event, boolean smelt, boolean teleportDrops) {
        if (!smelt && !teleportDrops) {
            return;
        }
        Block block = event.getBlock();
        Player player = event.getPlayer();
        World world = block.getWorld();
        if (world == null) {
            return;
        }
        Collection<ItemStack> drops = effectiveDrops(block, player, smelt);
        feature.compat.Blocks.suppressVanillaDrops(event); // suppress the vanilla drop; we place the effective drops below
        if (teleportDrops) {
            for (ItemStack drop : drops) {
                player.getInventory().addItem(drop).values()
                        .forEach(overflow -> world.dropItemNaturally(block.getLocation(), overflow));
            }
        } else { // smelt only — drop in-world, centred on the block
            Location at = block.getLocation().add(0.5, 0.5, 0.5);
            for (ItemStack drop : drops) {
                world.dropItemNaturally(at, drop);
            }
        }
    }

    /** The drops to place: the smelted product when SMELT applies and the block has one, else the natural drops. */
    private static Collection<ItemStack> effectiveDrops(Block block, Player player, boolean smelt) {
        if (smelt) {
            Material smelted = SMELT.get(block.getType());
            if (smelted != null) {
                return List.of(new ItemStack(smelted));
            }
        }
        return new ArrayList<>(block.getDrops(Hands.mainHand(player)));
    }

    private static Map<Material, Material> buildSmeltMap() {
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
        Material from = Material.matchMaterial(block);
        Material to = Material.matchMaterial(product);
        if (from != null && to != null) {
            map.put(from, to);
        }
    }
}
