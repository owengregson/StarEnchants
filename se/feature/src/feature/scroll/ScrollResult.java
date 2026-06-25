package feature.scroll;

import org.bukkit.inventory.ItemStack;

/**
 * Outcome of a scroll gesture, for {@link ScrollListener} to commit (§I). {@code produced} is an extra
 * item the scroll yields (e.g. the black scroll's extracted book), or {@code null}.
 */
public record ScrollResult(boolean commit, ItemStack newTarget, ItemStack produced, String message) {

    static ScrollResult unchanged(String message) {
        return new ScrollResult(false, null, null, message);
    }

    static ScrollResult committed(ItemStack newTarget, ItemStack produced, String message) {
        return new ScrollResult(true, newTarget, produced, message);
    }
}
