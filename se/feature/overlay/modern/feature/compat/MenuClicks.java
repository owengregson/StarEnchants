package feature.compat;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

/**
 * Modern {@code InventoryClickEvent.getClickedInventory()} access. Same-FQN counterpart to the
 * {@code overlay/legacy} impl, which computes the clicked inventory from the raw slot ({@code
 * getClickedInventory()} does not exist on 1.8.9 — docs/legacy-1.8.9-codeshare-design.md §4).
 */
public final class MenuClicks {

    private MenuClicks() {
    }

    public static Inventory clickedInventory(InventoryClickEvent event) {
        return event.getClickedInventory();
    }
}
