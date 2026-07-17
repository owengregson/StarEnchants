package feature.guard;

import item.head.IllusionMark;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.inventory.ItemStack;

/**
 * The canonical-item gate for mask-illusion heads (ADR-0064). A shown head exists ONLY inside equipment
 * packets, but a creative-mode client adopts its own entity's equipment packets into its local inventory and
 * echoes them back as authoritative slot writes — without this gate that echo REPLACES the real masked helmet
 * with the identityless dressed head (the "permanent masked helmet" brick). Two duties, both keyed on the
 * {@link IllusionMark}:
 *
 * <ul>
 *   <li><b>Deny adoption</b>: a marked stack arriving as a creative slot write is cancelled — the server's
 *       real item stands, losslessly.</li>
 *   <li><b>Canonicalize before ANY gesture</b>: a marked stack already in a real slot or on the cursor is
 *       undressed in place at {@code LOWEST}, before the {@code HIGH} apply-gesture family reads
 *       {@code getCurrentItem()} — every carrier then mutates the real helmet, never the visual.</li>
 * </ul>
 *
 * <p>Always-on (the {@link VanillaGuardListener} rule): keyed off item identity, never the masks toggle.
 * Floor-safe (1.8.9-present Bukkit API only). Folia-correct: both events fire on the clicking player's own
 * region thread and only that player's inventory is touched.
 */
public final class IllusionCanonGuard implements Listener {

    private final IllusionMark mark;

    public IllusionCanonGuard(IllusionMark mark) {
        this.mark = Objects.requireNonNull(mark, "mark");
    }

    // The client-sent replacement stack rides the creative event's cursor: a marked head there is the
    // write-back echo — deny it outright; the server's slot already holds the truth.
    @EventHandler(priority = EventPriority.LOWEST)
    @SuppressWarnings("deprecation") // updateInventory: the floor-stable resync (the MaskRemoveListener path)
    public void onCreative(InventoryCreativeEvent event) {
        if (!mark.isMarked(event.getCursor())) {
            return;
        }
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player player) {
            player.updateInventory(); // resync the lying client immediately
        }
    }

    // LOWEST: the repair must land before the HIGH gesture listeners read the slot/cursor.
    @EventHandler(priority = EventPriority.LOWEST)
    @SuppressWarnings("deprecation") // getCursor/setCursor: the floor-stable cursor path (ApplyGestureListener)
    public void onClick(InventoryClickEvent event) {
        if (event instanceof InventoryCreativeEvent) {
            return; // the creative lane above owns adoption; never repair under a client-authoritative write
        }
        ItemStack cursor = event.getCursor();
        if (mark.isMarked(cursor)) {
            event.setCursor(mark.undress(cursor)); // null payload → clear: the visual was never a real item
        }
        ItemStack current = event.getCurrentItem();
        if (mark.isMarked(current)) {
            event.setCurrentItem(mark.undress(current)); // canonicalize the TARGET before any carrier mutates it
        }
    }
}
