package feature.crystal;

import org.bukkit.inventory.ItemStack;

/**
 * The outcome of a crystal gesture (apply / merge), for {@link CrystalListener} to commit. {@code commit}
 * says the cursor and/or target stacks were mutated and must be written back; {@code newTarget} is what
 * the clicked slot becomes (the mutated gear on apply, a new multi-crystal on merge, or the original on a
 * consume-on-fail). {@code message} is the chat feedback (may be {@code null}).
 *
 * @param commit    whether the cursor/target were changed and should be committed
 * @param newTarget the item the clicked slot becomes (only read when {@code commit})
 * @param message   chat feedback, or {@code null} for none
 */
public record CrystalResult(boolean commit, ItemStack newTarget, String message) {

    /** Nothing changed (e.g. ineligible target / failed roll with no consume) — just relay {@code message}. */
    static CrystalResult unchanged(String message) {
        return new CrystalResult(false, null, message);
    }

    /** The stacks changed: {@code newTarget} replaces the clicked slot, the cursor is committed as-is. */
    static CrystalResult committed(ItemStack newTarget, String message) {
        return new CrystalResult(true, newTarget, message);
    }
}
