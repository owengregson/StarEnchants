package platform.item;

import java.util.Map;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** The feature-neutral give-with-overflow helper (ADR-0041): one home for the idiom that was inlined at ~16 sites. */
public final class Inventories {

    private Inventories() {
    }

    /**
     * Add {@code item} to {@code player}'s inventory, dropping any overflow at their feet. MUST be called on
     * the player's own region thread (inventory events + {@code Scheduling.onEntity} land there) so the drop is
     * in-region — Folia-safe.
     */
    public static void giveOrDrop(Player player, ItemStack item) {
        giveOrDrop(player, item, player.getLocation());
    }

    /**
     * As {@link #giveOrDrop(Player, ItemStack)} but dropping any overflow at {@code dropAt} (a same-region
     * location, e.g. the broken block) rather than at the player's feet.
     */
    public static void giveOrDrop(Player player, ItemStack item, Location dropAt) {
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        for (ItemStack over : overflow.values()) {
            dropAt.getWorld().dropItemNaturally(dropAt, over);
        }
    }
}
