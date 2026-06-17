package feature.scroll;

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
 * The scroll gesture UX (docs/v3-directives.md §I): holding a black/randomizer scroll on the CURSOR and
 * clicking a target applies it. Bukkit-thin glue — all logic is in {@link ScrollService}; this only
 * recognises the gesture, cancels the vanilla click, commits the mutated cursor/slot, and drops any
 * produced item (the black scroll's extracted book) into the player's inventory. Folia-correct: an
 * {@code InventoryClickEvent} fires on the clicking player's own region thread.
 */
public final class ScrollListener implements Listener {

    private final ScrollService service;

    public ScrollListener(ScrollService service) {
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
        if (!service.isScroll(cursor)) {
            return; // the cursor is not a scroll — leave the click alone
        }
        ItemStack target = event.getCurrentItem();
        if (target == null || target.getType() == Material.AIR || service.isScroll(target)) {
            return; // no target, or scroll-onto-scroll (meaningless)
        }

        event.setCancelled(true); // we own this interaction now
        ScrollResult result = service.interact(cursor, target);
        if (result.commit()) {
            event.setCursor(cursor.getAmount() <= 0 ? null : cursor);
            event.setCurrentItem(result.newTarget());
            if (result.produced() != null) {
                // Add the extracted book; drop any overflow at the player's feet (on their region thread).
                player.getInventory().addItem(result.produced()).values()
                        .forEach(extra -> player.getWorld().dropItemNaturally(player.getLocation(), extra));
            }
            player.updateInventory();
        }
        if (result.message() != null) {
            player.sendMessage(result.message());
        }
    }
}
