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
 * Slot-expander gesture glue (§H): slot item on the cursor + click on gear raises its enchant-slot count.
 * All logic lives in {@link SlotService}. Folia-correct: {@code InventoryClickEvent} fires on the clicking
 * player's region thread, so the cursor/inventory mutation is in-thread.
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
            return;
        }
        ItemStack target = event.getCurrentItem();
        if (target == null || target.getType() == Material.AIR || service.isSlotItem(target)) {
            return; // no target, or slot-onto-slot
        }

        event.setCancelled(true);
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
