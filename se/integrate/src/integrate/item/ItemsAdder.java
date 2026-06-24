package integrate.item;

import dev.lone.itemsadder.api.CustomStack;
import org.bukkit.inventory.ItemStack;

/**
 * ItemsAdder custom-item resolution (docs/decisions/0027): turns an {@code itemsadder:<namespace:id>} config
 * token into its custom {@link ItemStack}. Compiled against the real ItemsAdder API ({@code compileOnly});
 * loaded only when ItemsAdder is present (gated by {@link CustomItems}); fail-safe to {@code null}.
 */
final class ItemsAdder {

    private ItemsAdder() {
    }

    /** The custom ItemStack for {@code id} (an ItemsAdder namespaced id), or {@code null} if unknown. */
    static ItemStack resolve(String id) {
        try {
            CustomStack stack = CustomStack.getInstance(id);
            return stack == null ? null : stack.getItemStack();
        } catch (Throwable failed) {
            return null; // fail-safe: an unknown/odd id falls through to vanilla material resolution
        }
    }
}
