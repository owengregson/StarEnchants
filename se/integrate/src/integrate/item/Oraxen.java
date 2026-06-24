package integrate.item;

import io.th0rgal.oraxen.api.OraxenItems;
import io.th0rgal.oraxen.items.ItemBuilder;
import org.bukkit.inventory.ItemStack;

/**
 * Oraxen custom-item resolution (docs/decisions/0027): turns an {@code oraxen:<id>} config token into its
 * custom {@link ItemStack}. Compiled against the real Oraxen API ({@code compileOnly}); loaded only when
 * Oraxen is present (gated by {@link CustomItems}); fail-safe to {@code null}.
 */
final class Oraxen {

    private Oraxen() {
    }

    /** The custom ItemStack for Oraxen item {@code id}, or {@code null} if unknown. */
    static ItemStack resolve(String id) {
        try {
            if (!OraxenItems.exists(id)) {
                return null;
            }
            ItemBuilder builder = OraxenItems.getItemById(id);
            return builder == null ? null : builder.build();
        } catch (Throwable failed) {
            return null; // fail-safe: an unknown/odd id falls through to vanilla material resolution
        }
    }
}
