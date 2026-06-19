package feature.crystal;

import org.bukkit.inventory.ItemStack;

/**
 * The outcome of a crystal gesture (apply / merge / extract), for {@link CrystalListener} to commit.
 * {@code commit} says the cursor and/or target stacks were mutated and must be written back; {@code newTarget}
 * is what the clicked slot becomes (the mutated gear on apply/extract, a new multi-crystal on merge, or the
 * original on a consume-on-fail). {@code give} is an item to hand to the player on top of the slot/cursor
 * writes (the minted crystal an EXTRACT yields) — added to their inventory, overflow dropped at their feet;
 * {@code null} for apply/merge. {@code sound} is a configured namespaced sound token to play, or {@code null}.
 * {@code message} is the chat feedback (may be {@code null}).
 *
 * @param commit    whether the cursor/target were changed and should be committed
 * @param newTarget the item the clicked slot becomes (only read when {@code commit})
 * @param give      an extra item to give the player (the extracted crystal), or {@code null}
 * @param sound     a namespaced sound token to play on the player, or {@code null}
 * @param message   chat feedback, or {@code null} for none
 */
public record CrystalResult(boolean commit, ItemStack newTarget, ItemStack give, String sound, String message) {

    /** Nothing changed (e.g. ineligible target / failed roll with no consume) — just relay {@code message}. */
    static CrystalResult unchanged(String message) {
        return new CrystalResult(false, null, null, null, message);
    }

    /** The stacks changed: {@code newTarget} replaces the clicked slot, the cursor is committed as-is. */
    static CrystalResult committed(ItemStack newTarget, String sound, String message) {
        return new CrystalResult(true, newTarget, null, sound, message);
    }

    /** An extraction: the gear ({@code newTarget}) lost a crystal, the extractor cursor is spent, and {@code give} is the popped crystal. */
    static CrystalResult extracted(ItemStack newTarget, ItemStack give, String sound, String message) {
        return new CrystalResult(true, newTarget, give, sound, message);
    }
}
