package feature.compat;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;

/**
 * Clicked-inventory resolution — shared across eras (ADR-0044; docs/legacy-1.8.9-codeshare-design.md §4).
 * {@code InventoryClickEvent.getClickedInventory()} does not exist on 1.8.9, so the clicked inventory is derived
 * from the raw slot on every era: a raw slot inside the top inventory's size is the top, otherwise the bottom
 * (and a negative raw slot — a click outside any inventory — is none). This is exactly the mapping
 * {@code getClickedInventory()} implements by the {@code InventoryView} contract, so the derivation is
 * behaviour-identical on modern and correct on 1.8.
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
