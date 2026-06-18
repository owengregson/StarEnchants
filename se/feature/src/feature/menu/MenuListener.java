package feature.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * The single shared router for every StarEnchants menu (docs/v3-directives.md §K). It recognises our menus
 * by the {@link MenuHolder} on the view's top inventory and CANCELS every click and drag in them — the
 * menus are display-only, so no item can ever be inserted, removed or rearranged — then invokes the
 * {@link ClickAction} the menu bound to the clicked slot (if any). One listener serves all menus: there is
 * no per-menu registration, because the holder carries its own {@link Menu} and action bindings.
 *
 * <p>The event fires on the clicking player's region thread (Folia) / main thread (Paper), so the action
 * runs inline and may freely touch the clicking player's own inventory; cross-entity/world work inside an
 * action must hop through {@code Scheduling} itself (folia-scheduling).
 *
 * <p>Crystal / slot / carrier / scroll <em>drag gestures</em> operate in the player's normal inventory (no
 * {@code MenuHolder} open) through their own listeners and are unaffected — this listener only ever fires
 * when one of our menus is the open top inventory.
 */
public final class MenuListener implements Listener {

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
        ClickAction action = holder.actionAt(raw);
        if (action != null) {
            action.onClick(new MenuClick(player, holder, event.getClick()));
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) {
            event.setCancelled(true);
        }
    }
}
