package feature.scroll;

import org.bukkit.inventory.ItemStack;

/**
 * The outcome of a scroll gesture, for {@link ScrollListener} to commit (docs/v3-directives.md §I).
 * {@code commit} says the cursor and/or target changed (the scroll was consumed); {@code newTarget} is
 * what the clicked slot becomes; {@code produced} is an extra item the scroll yields (e.g. the black
 * scroll's extracted enchant book) to add to the player's inventory, or {@code null}; {@code message} is
 * chat feedback (may be {@code null}).
 */
public record ScrollResult(boolean commit, ItemStack newTarget, ItemStack produced, String message) {

    static ScrollResult unchanged(String message) {
        return new ScrollResult(false, null, null, message);
    }

    static ScrollResult committed(ItemStack newTarget, ItemStack produced, String message) {
        return new ScrollResult(true, newTarget, produced, message);
    }
}
