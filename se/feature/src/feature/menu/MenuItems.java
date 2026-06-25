package feature.menu;

import java.util.Map;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Small shared helpers for menus that hand items to a player. */
public final class MenuItems {

    private MenuItems() {
    }

    /**
     * Add {@code item} to {@code player}'s inventory, dropping any overflow at their feet. MUST be called on
     * the player's own region thread (a menu click handler runs there) so the drop is in-region — Folia-safe.
     */
    public static void giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        for (ItemStack over : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), over);
        }
    }
}
