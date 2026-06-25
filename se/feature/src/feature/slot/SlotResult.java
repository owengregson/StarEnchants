package feature.slot;

import org.bukkit.inventory.ItemStack;

/**
 * Outcome of a slot-expander gesture for {@link SlotListener} to commit (§H). {@code commit} false ==
 * no-op (slot item preserved); {@code message} may be {@code null}.
 */
public record SlotResult(boolean commit, ItemStack newTarget, String message) {

    static SlotResult unchanged(String message) {
        return new SlotResult(false, null, message);
    }

    static SlotResult committed(ItemStack newTarget, String message) {
        return new SlotResult(true, newTarget, message);
    }
}
