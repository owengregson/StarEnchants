package feature.menu;

import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * Routes inventory interaction in a StarEnchants menu to the {@link EnchantMenu} (docs/architecture.md §7).
 * Recognises our menus by the {@link MenuHolder} on the view's top inventory and CANCELS every click and
 * drag in them — the menus are display-only, so no item can ever be inserted, removed, or rearranged —
 * then routes a click on an icon to the menu's handler. The event fires on the clicking player's region
 * thread, so the handler (which reads/writes that player's own inventory) runs inline and Folia-correct.
 */
public final class MenuListener implements Listener {

    private final EnchantMenu menu;

    public MenuListener(EnchantMenu menu) {
        this.menu = Objects.requireNonNull(menu, "menu");
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder holder)) {
            return; // not one of our menus
        }
        event.setCancelled(true); // display-only: never let an item move, even in the player's own grid below
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int raw = event.getRawSlot();
        if (raw < 0 || raw >= event.getView().getTopInventory().getSize()) {
            return; // a click in the player's own inventory (below our menu) — cancelled above, nothing to do
        }
        menu.handleClick(player, holder, raw);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) {
            event.setCancelled(true);
        }
    }
}
