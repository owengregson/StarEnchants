package feature.heroic;

import org.bukkit.inventory.ItemStack;

/**
 * Outcome of a heroic upgrade gesture, for {@link HeroicListener} to commit. {@code newTarget} is the
 * upgraded piece, or the original on a failed roll (the upgrade is consumed either way).
 */
public record HeroicResult(boolean commit, ItemStack newTarget, String message) {

    static HeroicResult unchanged(String message) {
        return new HeroicResult(false, null, message);
    }

    static HeroicResult committed(ItemStack newTarget, String message) {
        return new HeroicResult(true, newTarget, message);
    }
}
