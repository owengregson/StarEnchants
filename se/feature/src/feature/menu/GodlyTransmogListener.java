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
 * The physical godly-transmog gesture (docs/v3-directives.md §I/§K): drag the godly-transmog tool onto an
 * enchanted gear piece in your own inventory to OPEN the deterministic enchant-reorder GUI bound to THAT
 * piece (unlike the transmog SCROLL, which is a one-shot random shuffle consumed on use). Mirrors the
 * crystal/scroll drag gestures (cursor-onto-target, bottom inventory only), but instead of mutating the
 * gear on the spot it just opens the menu — the tool is NOT consumed.
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
            case LEFT, RIGHT -> { /* a deliberate place-the-tool-onto-gear click */ }
            default -> {
                return;
            }
        }
        // The gear must be clicked in the player's OWN inventory, with the godly tool on the cursor.
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
        // Only enchanted gear has an order to reorder; on anything else the gesture is a no-op (the tool
        // stays on the cursor so the player can try another piece).
        if (combat.read(target).enchants().isEmpty()) {
            return;
        }
        event.setCancelled(true); // own the interaction; the tool is NOT consumed (it just opens the GUI)
        menu.open(player, event.getSlot()); // open the reorder GUI bound to the clicked slot (region-hops)
    }
}
