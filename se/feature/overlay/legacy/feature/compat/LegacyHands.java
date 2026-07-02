package feature.compat;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Legacy (1.8.9) impl of {@link Hands} — the era-exclusive {@code overlay/legacy} hand access (ADR-0044; §4).
 * 1.8 has a single hand ({@code getItemInHand}) and no off-hand, no {@code PlayerInteractEvent.getHand()}, and
 * no {@code ClickType.SWAP_OFFHAND}: off-hand reads return nothing and off-hand writes are no-ops.
 */
public final class LegacyHands implements Hands {

    @Override
    @SuppressWarnings("deprecation") // getItemInHand is the 1.8 main-hand accessor
    public ItemStack mainHand(Player player) {
        return player.getInventory().getItemInHand();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void setMainHand(Player player, ItemStack item) {
        player.getInventory().setItemInHand(item);
    }

    @Override
    @SuppressWarnings("deprecation")
    public ItemStack mainHand(LivingEntity entity) {
        return entity.getEquipment() == null ? null : entity.getEquipment().getItemInHand();
    }

    /** 1.8 fires interact only for the single hand, so every interact is "main hand". */
    @Override
    public boolean isMainHand(PlayerInteractEvent event) {
        return true;
    }

    /** 1.8 has no off-hand-swap click. */
    @Override
    public boolean isOffhandSwap(ClickType type) {
        return false;
    }

    /** 1.8 has no off-hand. */
    @Override
    public ItemStack offHand(Player player) {
        return null;
    }

    /** 1.8 has no off-hand — no-op. */
    @Override
    public void setOffHand(Player player, ItemStack item) {
    }

    @Override
    @SuppressWarnings("deprecation") // 1.8 has no storage/off-hand split; getContents() is the inventory
    public ItemStack[] storageContents(Player player) {
        return player.getInventory().getContents();
    }
}
