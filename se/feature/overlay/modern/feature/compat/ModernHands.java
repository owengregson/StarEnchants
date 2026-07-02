package feature.compat;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Modern (1.9+) impl of {@link Hands} — the era-exclusive {@code overlay/modern} hand access (ADR-0044; §4).
 * Reads the split main/off-hand item API and the per-hand interact/click signals that do not exist on 1.8.9.
 */
public final class ModernHands implements Hands {

    @Override
    public ItemStack mainHand(Player player) {
        return player.getInventory().getItemInMainHand();
    }

    @Override
    public void setMainHand(Player player, ItemStack item) {
        player.getInventory().setItemInMainHand(item);
    }

    @Override
    public ItemStack mainHand(LivingEntity entity) {
        return entity.getEquipment() == null ? null : entity.getEquipment().getItemInMainHand();
    }

    @Override
    public boolean isMainHand(PlayerInteractEvent event) {
        return event.getHand() == EquipmentSlot.HAND;
    }

    @Override
    public boolean isOffhandSwap(ClickType type) {
        return type == ClickType.SWAP_OFFHAND;
    }

    @Override
    public ItemStack offHand(Player player) {
        return player.getInventory().getItemInOffHand();
    }

    @Override
    public void setOffHand(Player player, ItemStack item) {
        player.getInventory().setItemInOffHand(item);
    }

    @Override
    public ItemStack[] storageContents(Player player) {
        return player.getInventory().getStorageContents();
    }
}
