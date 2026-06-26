package feature.compat;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Legacy (1.8.9) hand/equipment access — same-FQN counterpart to the {@code overlay/modern} impl. 1.8 has a
 * single hand ({@code getItemInHand}) and no off-hand, no {@code PlayerInteractEvent.getHand()}, and no
 * {@code ClickType.SWAP_OFFHAND} (docs/legacy-1.8.9-codeshare-design.md §4). Off-hand reads return nothing;
 * off-hand writes are no-ops.
 */
public final class Hands {

    private Hands() {
    }

    @SuppressWarnings("deprecation") // getItemInHand is the 1.8 main-hand accessor
    public static ItemStack mainHand(Player player) {
        return player.getInventory().getItemInHand();
    }

    @SuppressWarnings("deprecation")
    public static void setMainHand(Player player, ItemStack item) {
        player.getInventory().setItemInHand(item);
    }

    @SuppressWarnings("deprecation")
    public static ItemStack mainHand(LivingEntity entity) {
        return entity.getEquipment() == null ? null : entity.getEquipment().getItemInHand();
    }

    /** 1.8 fires interact only for the single hand, so every interact is "main hand". */
    public static boolean isMainHand(PlayerInteractEvent event) {
        return true;
    }

    /** 1.8 has no off-hand-swap click. */
    public static boolean isOffhandSwap(ClickType type) {
        return false;
    }

    /** 1.8 has no off-hand. */
    public static ItemStack offHand(Player player) {
        return null;
    }

    /** 1.8 has no off-hand — no-op. */
    public static void setOffHand(Player player, ItemStack item) {
    }

    @SuppressWarnings("deprecation") // 1.8 has no storage/off-hand split; getContents() is the inventory
    public static ItemStack[] storageContents(Player player) {
        return player.getInventory().getContents();
    }
}
