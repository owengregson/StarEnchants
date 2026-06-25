package feature.menu;

import feature.scroll.ScrollService;
import item.codec.CombatCodec;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * The physical godly-transmog gesture (§I/§K): drag the tool onto enchanted gear in your own inventory to
 * open the reorder GUI bound to that piece. Unlike the transmog scroll (one-shot random, consumed), the
 * tool just opens the menu and is NOT consumed.
 */
public final class GodlyTransmogListener implements Listener {

    private final ScrollService scrolls;
    private final GodlyTransmogMenu menu;
    private final CombatCodec combat;

    public GodlyTransmogListener(ScrollService scrolls, GodlyTransmogMenu menu, CombatCodec combat) {
        this.scrolls = Objects.requireNonNull(scrolls, "scrolls");
        this.menu = Objects.requireNonNull(menu, "menu");
        this.combat = Objects.requireNonNull(combat, "combat");
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        switch (event.getClick()) {
            case LEFT, RIGHT -> { /* a deliberate place-tool-onto-gear click */ }
            default -> {
                return;
            }
        }
        // Gesture rule: the gear is clicked in the player's own inventory with the tool on the cursor.
        if (event.getClickedInventory() == null
                || event.getClickedInventory() != event.getView().getBottomInventory()) {
            return;
        }
        ItemStack cursor = event.getCursor();
        if (!scrolls.isGodlyTransmog(cursor)) {
            return;
        }
        ItemStack target = event.getCurrentItem();
        if (target == null || target.getType().isAir()) {
            return;
        }
        // No enchants = nothing to reorder; no-op, leaving the tool on the cursor for another piece.
        if (combat.read(target).enchants().isEmpty()) {
            return;
        }
        event.setCancelled(true); // own the interaction; the tool is NOT consumed
        menu.open(player, event.getSlot()); // bound to the clicked slot; region-hops
    }
}
