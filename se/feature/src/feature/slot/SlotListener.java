package feature.slot;

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
 * The slot expander / gem gesture UX (docs/v3-directives.md §H): holding a slot item on the CURSOR and
 * clicking a piece of gear raises its enchant-slot count. Bukkit-thin glue — all logic is in
 * {@link SlotService}; this only recognises the gesture, cancels the vanilla click, and commits the
 * mutated cursor/slot. Folia-correct: an {@code InventoryClickEvent} fires on the clicking player's own
 * region thread, so mutating their cursor/inventory is in-thread.
 */
public final class SlotListener implements Listener {

    private final SlotService service;

    public SlotListener(SlotService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    @SuppressWarnings("deprecation") // setCursor/getView: the floor-stable cursor/view path
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) {
            return;
        }
        if (event.getClickedInventory() == null
                || event.getClickedInventory() != event.getView().getBottomInventory()) {
            return;
        }
        ItemStack cursor = event.getCursor();
        if (!service.isSlotItem(cursor)) {
            return; // the cursor is not a slot item — leave the click alone
        }
        ItemStack target = event.getCurrentItem();
        if (target == null || target.getType() == Material.AIR || service.isSlotItem(target)) {
            return; // no target, or slot-item-onto-slot-item (meaningless)
        }

        event.setCancelled(true); // we own this interaction now
        SlotResult result = service.applyTo(cursor, target);
        if (result.commit()) {
            event.setCursor(cursor.getAmount() <= 0 ? null : cursor);
            event.setCurrentItem(result.newTarget());
            player.updateInventory();
        }
        if (result.message() != null) {
            player.sendMessage(result.message());
        }
    }
}
