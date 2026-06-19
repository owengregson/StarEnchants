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
 * The crystal gesture UX (docs/v3-directives.md §E): holding a crystal on the CURSOR and clicking gear
 * applies it; clicking another crystal merges them into a multi-crystal; holding a crystal EXTRACTOR and
 * clicking crystal-bearing gear pops its last crystal back into a whole crystal item. Bukkit-thin glue —
 * all logic is in {@link CrystalService}; this only recognises the gesture, cancels the vanilla click, and
 * commits the mutated cursor/slot (and hands over an extracted crystal, plays the configured sound).
 * Folia-correct: an {@code InventoryClickEvent} fires on the clicking player's own region thread, so
 * mutating that player's cursor/inventory and playing a sound at their location is all in-thread.
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
        // A plain LEFT/RIGHT place, or the directive-named SWAP_WITH_CURSOR (drag crystal-onto-crystal merge),
        // onto a slot in the player's OWN inventory grid — never shift/number/double clicks nor other GUIs.
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
            return; // no target
        }
        boolean extractor = service.isExtractor(cursor);
        if (!extractor && !service.isCrystal(cursor)) {
            return; // the cursor is neither a crystal nor an extractor — leave the click alone
        }

        event.setCancelled(true); // we own this interaction now (apply / merge / extract)
        CrystalResult result = extractor ? service.extract(cursor, target) : service.interact(cursor, target);
        if (result.commit()) {
            event.setCursor(cursor.getAmount() <= 0 ? null : cursor);
            event.setCurrentItem(result.newTarget() != null && result.newTarget().getAmount() <= 0
                    ? null : result.newTarget());
            if (result.give() != null) {
                // Hand over the extracted crystal: into the inventory, overflow dropped at the player's feet
                // (on their own region thread — the event fires there, so this is in-thread on Folia).
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
