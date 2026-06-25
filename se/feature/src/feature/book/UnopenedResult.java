package feature.book;

import org.bukkit.inventory.ItemStack;

/** The outcome of opening an unopened/randomized book (§I), for {@link UnopenedBookListener} to commit. */
public record UnopenedResult(boolean opened, ItemStack produced, String message) {

    static UnopenedResult nothing(String message) {
        return new UnopenedResult(false, null, message);
    }

    static UnopenedResult opened(ItemStack produced, String message) {
        return new UnopenedResult(true, produced, message);
    }
}
