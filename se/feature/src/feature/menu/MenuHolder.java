package feature.menu;

import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * The {@link InventoryHolder} that tags a StarEnchants menu inventory (docs/architecture.md §7). The
 * click listener recognises one of our menus by {@code event.getView().getTopInventory().getHolder()
 * instanceof MenuHolder} — the VIEW's top inventory, not {@code event.getInventory()} (which is the
 * <em>clicked</em> inventory and is the player's own grid for a bottom-row click) — so vanilla
 * containers and other plugins' inventories are never touched, and a click anywhere in the open view is
 * still attributed to our menu. Carries the current page so the listener can paginate and re-resolve the
 * clicked enchant.
 */
public final class MenuHolder implements InventoryHolder {

    private final int page;
    private Inventory inventory;

    MenuHolder(int page) {
        this.page = page;
    }

    int page() {
        return page;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        // Never null in practice (set immediately after creation); a defensive empty chest avoids an NPE
        // if Bukkit queries the holder before the inventory is assigned.
        return inventory != null ? inventory
                : org.bukkit.Bukkit.createInventory(this, InventoryType.CHEST);
    }
}
