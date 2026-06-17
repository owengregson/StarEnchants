package feature.soul;

import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;

/**
 * The soul gem's inventory affordances (docs/v3-directives.md §D): <strong>combine</strong> two gems by
 * dropping one onto the other (their souls sum into a fresh gem, with the configured combine sound), and
 * the <strong>anti-dupe</strong> guards (a gem can never be placed as a block, nor used as a crafting
 * ingredient — AE's {@code SoulgemCraftEvent} analog). All gestures fire on the acting player's own region
 * thread, so the inventory mutations are in-thread and Folia-correct.
 */
public final class SoulInventoryListener implements Listener {

    private final SoulService souls;

    public SoulInventoryListener(SoulService souls) {
        this.souls = Objects.requireNonNull(souls, "souls");
    }

    /**
     * Drop a gem (the cursor) onto another gem (the clicked slot) to merge them. A plain LEFT click with
     * a single gem on the cursor and a single gem under it sums into a fresh gem in the slot and clears
     * the cursor. Any other shape is left to vanilla (distinct gems never auto-stack, so a normal pickup
     * is unaffected).
     */
    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (event.getClick() != ClickType.LEFT || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();
        if (cursor == null || current == null || cursor.getAmount() != 1 || current.getAmount() != 1) {
            return; // a stacked gem (impossible for distinct PDC) or a non-merge click — leave it
        }
        if (!souls.isGem(cursor) || !souls.isGem(current)) {
            return; // not two gems — not a merge
        }
        ItemStack merged = souls.combine(player, cursor, current);
        if (merged == null) {
            return;
        }
        event.setCancelled(true);
        event.setCurrentItem(merged);
        player.setItemOnCursor(null);
    }

    /** A gem is never placeable as a block — cancel the place so a placeable-material gem cannot vanish. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (souls.isGem(event.getItemInHand())) {
            event.setCancelled(true);
        }
    }

    /** Blank the crafting result the moment a gem sits in the matrix, so it can never be consumed as an ingredient. */
    @EventHandler(ignoreCancelled = true)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (hasGem(event.getInventory().getMatrix())) {
            event.getInventory().setResult(null);
        }
    }

    /** Belt-and-braces: cancel a craft that somehow reaches commit with a gem in the matrix. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (hasGem(event.getInventory().getMatrix())) {
            event.setCancelled(true);
        }
    }

    private boolean hasGem(ItemStack[] matrix) {
        if (matrix == null) {
            return false;
        }
        for (ItemStack item : matrix) {
            if (souls.isGem(item)) {
                return true;
            }
        }
        return false;
    }
}
