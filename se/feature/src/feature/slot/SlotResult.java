package feature.slot;

import org.bukkit.inventory.ItemStack;

/**
 * The outcome of a slot-expander / slot-gem gesture, for {@link SlotListener} to commit (docs/v3-directives.md
 * §H). {@code commit} says the cursor and/or target changed (the slot item was consumed and the gear's slot
 * count rose); {@code newTarget} is what the clicked slot becomes (the gear with more slots); {@code message}
 * is chat feedback (may be {@code null}).
 */
public record SlotResult(boolean commit, ItemStack newTarget, String message) {

    static SlotResult unchanged(String message) {
        return new SlotResult(false, null, message);
    }

    static SlotResult committed(ItemStack newTarget, String message) {
        return new SlotResult(true, newTarget, message);
    }
}
