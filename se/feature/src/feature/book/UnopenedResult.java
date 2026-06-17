package feature.book;

import org.bukkit.inventory.ItemStack;

/**
 * The outcome of opening an unopened/randomized book (docs/v3-directives.md §I), for
 * {@link UnopenedBookListener} to commit. {@code opened} says one unopened book was consumed and a
 * concrete book rolled; {@code produced} is the rolled enchant book to give the player (or {@code null}
 * when nothing was rolled); {@code message} is chat feedback (may be {@code null}).
 */
public record UnopenedResult(boolean opened, ItemStack produced, String message) {

    static UnopenedResult nothing(String message) {
        return new UnopenedResult(false, null, message);
    }

    static UnopenedResult opened(ItemStack produced, String message) {
        return new UnopenedResult(true, produced, message);
    }
}
