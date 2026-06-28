package feature.slot;

import org.bukkit.inventory.ItemStack;

/**
 * Outcome of a slot-expander gesture for {@link SlotListener} to commit (§H). {@code commit} = the gear
 * changed (write it back); {@code consume} = the orb was spent (write the decremented cursor back). A no-op
 * (orb preserved, gear unchanged) is {@link #unchanged}; a successful apply is {@link #committed} (both true);
 * a failed apply roll is {@link #failed} (orb consumed, gear unchanged).
 */
public record SlotResult(boolean commit, boolean consume, ItemStack newTarget, String message) {

    static SlotResult unchanged(String message) {
        return new SlotResult(false, false, null, message);
    }

    static SlotResult committed(ItemStack newTarget, String message) {
        return new SlotResult(true, true, newTarget, message);
    }

    /** A failed apply roll: the orb is consumed (already decremented) but the gear is untouched. */
    static SlotResult failed(String message) {
        return new SlotResult(false, true, null, message);
    }
}
