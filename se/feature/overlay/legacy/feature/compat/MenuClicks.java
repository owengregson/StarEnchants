package feature.compat;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;

/**
 * Legacy (1.8.9) clicked-inventory resolution — same-FQN counterpart to the {@code overlay/modern} impl.
 * 1.8 has no {@code InventoryClickEvent.getClickedInventory()}, so it is derived from the raw slot: a raw
 * slot inside the top inventory's size is the top, otherwise the bottom (and a negative raw slot — a click
 * outside any inventory — is none). docs/legacy-1.8.9-codeshare-design.md §4.
 */
public final class MenuClicks {

    private MenuClicks() {
    }

    public static Inventory clickedInventory(InventoryClickEvent event) {
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0) {
            return null; // clicked outside any inventory
        }
        InventoryView view = event.getView();
        return rawSlot < view.getTopInventory().getSize() ? view.getTopInventory() : view.getBottomInventory();
    }
}
