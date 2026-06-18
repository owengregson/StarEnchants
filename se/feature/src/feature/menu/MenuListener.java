package feature.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * The single shared router for every StarEnchants menu (docs/v3-directives.md §K). It recognises our menus
 * by the {@link MenuHolder} on the view's top inventory and routes clicks to the {@link ClickAction} a menu
 * bound to the clicked slot. One listener serves all menus — the holder carries its own {@link Menu} and
 * action bindings.
 *
 * <p><strong>Display menus</strong> (the default): every click and drag is cancelled — nothing can be moved.
 *
 * <p><strong>{@link InteractiveMenu} benches</strong>: the declared {@link InteractiveMenu#inputSlots() input
 * slots} accept normal pickup/place; every other top slot (buttons + filler) is locked and runs its bound
 * action; the slot-crossing click types (shift, number-key, double-click, drag, swap-offhand, drop) are
 * cancelled so an item can only ever land in an input slot or the player's own inventory. On close, the
 * menu's {@link InteractiveMenu#onClose} returns any staged inputs — a closed bench never eats items.
 *
 * <p>The event fires on the clicking player's region thread (Folia) / main thread (Paper), so actions run
 * inline and may touch the clicking player's own inventory; cross-entity work must hop through
 * {@code Scheduling} (folia-scheduling). Crystal/slot/carrier drag gestures operate in the player's normal
 * inventory (no {@code MenuHolder} open) and are unaffected.
 */
public final class MenuListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder holder)) {
            return; // not one of our menus
        }
        if (holder.menu() instanceof InteractiveMenu form) {
            handleInteractiveClick(event, holder, form);
            return;
        }
        // Display-only: never let an item move, even in the player's own grid below.
        event.setCancelled(true);
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

    /** Route a click in a bench: allow placement in an input slot, run a button, else lock. */
    private void handleInteractiveClick(InventoryClickEvent event, MenuHolder holder, InteractiveMenu form) {
        ClickType type = event.getClick();
        // Cancel every action that could shuttle an item across slots (into a locked/button slot).
        if (type.isShiftClick() || type == ClickType.NUMBER_KEY || type == ClickType.DOUBLE_CLICK
                || type == ClickType.SWAP_OFFHAND || type == ClickType.DROP || type == ClickType.CONTROL_DROP) {
            event.setCancelled(true);
            return;
        }
        int raw = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();
        boolean topClick = raw >= 0 && raw < topSize;
        if (topClick && form.inputSlots().contains(raw)) {
            return; // an input slot — allow the normal pickup/place
        }
        if (topClick) {
            event.setCancelled(true); // a locked top slot (button / filler)
            if (event.getWhoClicked() instanceof Player player) {
                ClickAction action = holder.actionAt(raw);
                if (action != null) {
                    action.onClick(new MenuClick(player, holder, type));
                }
            }
            return;
        }
        // A plain click in the player's own (bottom) inventory: safe to allow (it cannot reach a top slot).
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        // Cancel drags in any of our menus — benches take items via click (a drag could span locked slots).
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder holder
                && holder.menu() instanceof InteractiveMenu form
                && event.getPlayer() instanceof Player player) {
            form.onClose(player, holder); // return any staged inputs
        }
    }
}
