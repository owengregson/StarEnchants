package feature.combat;

import feature.compat.DropControl;
import feature.compat.Hands;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import platform.item.SmeltTable;

/**
 * Applies the MINE-side {@code SMELT} (block→smelted product) and {@code TELEPORT_DROPS} (drops to breaker's
 * inventory) read-backs (Cosmic Enchants-style parity) to a {@link BlockBreakEvent}, on the firing block's
 * region thread. Either flag suppresses the vanilla drop ({@link BlockBreakEvent#setDropItems(boolean)},
 * whole 1.17.1→26.1.x range) and places the effective drops itself, so there is no duplication.
 *
 * <p>The recipe table itself is {@link SmeltTable}, shared with {@code BREAK_BLOCK}'s volume-scoped
 * {@code smelt} so the two drop-transform paths can never disagree about what an ore becomes.
 */
public final class MineDrops {

    private MineDrops() {
    }

    /** Apply the requested MINE drop transforms to {@code event}. A no-op when neither flag is set. */
    public static void apply(BlockBreakEvent event, boolean smelt, boolean teleportDrops, Hands hands,
                             DropControl dropControl) {
        if (!smelt && !teleportDrops) {
            return;
        }
        Block block = event.getBlock();
        Player player = event.getPlayer();
        World world = block.getWorld();
        if (world == null) {
            return;
        }
        Collection<ItemStack> drops = effectiveDrops(block, player, smelt, hands);
        dropControl.suppressVanillaDrops(event); // suppress the vanilla drop; we place the effective drops below
        if (teleportDrops) {
            for (ItemStack drop : drops) {
                // Overflow drops at the BLOCK (not the player's feet), matching the pre-ADR-0041 behaviour.
                platform.item.Inventories.giveOrDrop(player, drop, block.getLocation());
            }
        } else { // smelt only — drop in-world, centred on the block
            Location at = block.getLocation().add(0.5, 0.5, 0.5);
            for (ItemStack drop : drops) {
                world.dropItemNaturally(at, drop);
            }
        }
    }

    /** The drops to place: the smelted product when SMELT applies and the block has one, else the natural drops. */
    private static Collection<ItemStack> effectiveDrops(Block block, Player player, boolean smelt, Hands hands) {
        if (smelt) {
            Material smelted = SmeltTable.productOf(block.getType());
            if (smelted != null) {
                return List.of(new ItemStack(smelted));
            }
        }
        return new ArrayList<>(block.getDrops(hands.mainHand(player)));
    }
}
