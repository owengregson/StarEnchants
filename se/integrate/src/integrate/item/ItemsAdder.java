package integrate.item;

import dev.lone.itemsadder.api.CustomStack;
import org.bukkit.inventory.ItemStack;

/** ItemsAdder custom-item resolution (docs/decisions/0027): an {@code itemsadder:} id → its {@link ItemStack}. */
final class ItemsAdder {

    private ItemsAdder() {
    }

    static ItemStack resolve(String id) {
        try {
            CustomStack stack = CustomStack.getInstance(id);
            return stack == null ? null : stack.getItemStack();
        } catch (Throwable failed) {
            return null; // fail-safe: an unknown/odd id falls through to vanilla material resolution
        }
    }
}
