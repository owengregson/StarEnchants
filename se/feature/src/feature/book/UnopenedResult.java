package feature.book;

import org.bukkit.inventory.ItemStack;

/**
 * The outcome of opening an unopened/randomized book (§I), for {@link UnopenedBookListener} to commit.
 * {@code opened} = one book consumed and a concrete book rolled; {@code produced} is the rolled book to
 * give ({@code null} when nothing rolled); {@code message} is chat feedback (may be {@code null}).
 */
public record UnopenedResult(boolean opened, ItemStack produced, String message) {

    static UnopenedResult nothing(String message) {
        return new UnopenedResult(false, null, message);
    }

    static UnopenedResult opened(ItemStack produced, String message) {
        return new UnopenedResult(true, produced, message);
    }
}
