package integrate.item;

import io.th0rgal.oraxen.api.OraxenItems;
import io.th0rgal.oraxen.items.ItemBuilder;
import org.bukkit.inventory.ItemStack;

/** Oraxen custom-item resolution (docs/decisions/0027): an {@code oraxen:} id → its {@link ItemStack}. */
final class Oraxen {

    private Oraxen() {
    }

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
