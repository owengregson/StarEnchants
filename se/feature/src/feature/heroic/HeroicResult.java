package feature.heroic;

import org.bukkit.inventory.ItemStack;

/**
 * The outcome of a heroic upgrade gesture, for {@link HeroicListener} to commit. {@code commit} says
 * the cursor and/or target changed (the upgrade was consumed and/or the piece was replaced);
 * {@code newTarget} is what the clicked slot becomes (the upgraded piece, or the original on a failed
 * roll); {@code message} is chat feedback (may be {@code null}).
 */
public record HeroicResult(boolean commit, ItemStack newTarget, String message) {

    static HeroicResult unchanged(String message) {
        return new HeroicResult(false, null, message);
    }

    static HeroicResult committed(ItemStack newTarget, String message) {
        return new HeroicResult(true, newTarget, message);
    }
}
