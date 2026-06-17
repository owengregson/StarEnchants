package feature.crystal;

import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * The crystal gesture UX (docs/v3-directives.md §E): holding a crystal on the CURSOR and clicking gear
 * applies it; clicking another crystal merges them into a multi-crystal. Bukkit-thin glue — all logic is
 * in {@link CrystalService}; this only recognises the gesture, cancels the vanilla click, and commits the
 * mutated cursor/slot. Folia-correct: an {@code InventoryClickEvent} fires on the clicking player's own
 * region thread, so mutating that player's cursor/inventory is in-thread.
 */
public final class CrystalListener implements Listener {

    private final CrystalService service;

    public CrystalListener(CrystalService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    @SuppressWarnings("deprecation") // setCursor/getView: the floor-stable cursor/view path
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        // Only a plain LEFT/RIGHT place onto a slot in the player's OWN inventory grid — never shift/
        // number/double clicks (dupe + double-click-collect misfires) nor another container/GUI's slots.
        if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) {
            return;
        }
        if (event.getClickedInventory() == null
                || event.getClickedInventory() != event.getView().getBottomInventory()) {
            return;
        }
        ItemStack cursor = event.getCursor();
        if (!service.isCrystal(cursor)) {
            return; // the cursor is not a crystal — leave the click alone
        }
        ItemStack target = event.getCurrentItem();
        if (target == null || target.getType() == Material.AIR) {
            return; // no target
        }

        event.setCancelled(true); // we own this interaction now (apply onto gear, or merge onto a crystal)
        CrystalResult result = service.interact(cursor, target);
        if (result.commit()) {
            event.setCursor(cursor.getAmount() <= 0 ? null : cursor);
            event.setCurrentItem(result.newTarget() != null && result.newTarget().getAmount() <= 0
                    ? null : result.newTarget());
            player.updateInventory();
        }
        if (result.message() != null) {
            player.sendMessage(result.message());
        }
    }
}
