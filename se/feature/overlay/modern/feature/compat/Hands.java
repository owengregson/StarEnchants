package feature.compat;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Modern (1.9+) hand/equipment access for the feature shells — the one place the off-hand / main-hand item
 * API is touched, so the shells stay version-agnostic (docs/legacy-1.8.9-codeshare-design.md §4). Same-FQN
 * counterpart to the {@code overlay/legacy} impl, which collapses everything to the single 1.8 hand.
 */
public final class Hands {

    private Hands() {
    }

    public static ItemStack mainHand(Player player) {
        return player.getInventory().getItemInMainHand();
    }

    public static void setMainHand(Player player, ItemStack item) {
        player.getInventory().setItemInMainHand(item);
    }

    /** The living entity's main-hand item, or {@code null} if it has no equipment. */
    public static ItemStack mainHand(LivingEntity entity) {
        return entity.getEquipment() == null ? null : entity.getEquipment().getItemInMainHand();
    }

    /** Whether an interact fired for the MAIN hand (1.9 fires twice — once per hand; 1.8 only the main). */
    public static boolean isMainHand(PlayerInteractEvent event) {
        return event.getHand() == EquipmentSlot.HAND;
    }

    /** Whether a click is the off-hand-swap key ({@code F}); always false on 1.8 (no off-hand). */
    public static boolean isOffhandSwap(ClickType type) {
        return type == ClickType.SWAP_OFFHAND;
    }

    public static ItemStack offHand(Player player) {
        return player.getInventory().getItemInOffHand();
    }

    public static void setOffHand(Player player, ItemStack item) {
        player.getInventory().setItemInOffHand(item);
    }

    /** The main storage contents (excludes armour + off-hand on modern). */
    public static ItemStack[] storageContents(Player player) {
        return player.getInventory().getStorageContents();
    }
}
