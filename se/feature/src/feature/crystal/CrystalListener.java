package feature.crystal;

import java.util.Map;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Crystal gesture glue (docs/v3-directives.md §E); logic lives in {@link CrystalService}. Folia-correct:
 * {@code InventoryClickEvent} fires on the clicking player's own region thread, so mutating their
 * cursor/inventory and playing a sound at their location is in-thread.
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
        // LEFT/RIGHT place or SWAP_WITH_CURSOR (crystal-onto-crystal merge), player's own grid only — never
        // shift/number/double clicks nor other GUIs.
        boolean ours = event.getClick() == ClickType.LEFT || event.getClick() == ClickType.RIGHT
                || event.getAction() == InventoryAction.SWAP_WITH_CURSOR;
        if (!ours) {
            return;
        }
        if (event.getClickedInventory() == null
                || event.getClickedInventory() != event.getView().getBottomInventory()) {
            return;
        }
        ItemStack cursor = event.getCursor();
        ItemStack target = event.getCurrentItem();
        if (target == null || target.getType() == Material.AIR) {
            return;
        }
        boolean extractor = service.isExtractor(cursor);
        if (!extractor && !service.isCrystal(cursor)) {
            return; // cursor is neither crystal nor extractor — leave the click alone
        }

        event.setCancelled(true);
        CrystalResult result = extractor ? service.extract(cursor, target) : service.interact(cursor, target);
        if (result.commit()) {
            event.setCursor(cursor.getAmount() <= 0 ? null : cursor);
            event.setCurrentItem(result.newTarget() != null && result.newTarget().getAmount() <= 0
                    ? null : result.newTarget());
            if (result.give() != null) {
                // Hand over the extracted crystal; overflow drops at the player's feet (in-thread on Folia).
                Map<Integer, ItemStack> overflow = player.getInventory().addItem(result.give());
                overflow.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
            }
            player.updateInventory();
        }
        if (result.sound() != null) {
            player.playSound(player.getLocation(), result.sound(), 1.0f, 1.0f);
        }
        if (result.message() != null) {
            player.sendMessage(result.message());
        }
    }
}
