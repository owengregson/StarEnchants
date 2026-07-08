package item.codec;

import org.bukkit.inventory.ItemStack;

/**
 * Marks / detects a right-click USE-ITEM (§3, docs/decisions/0048-use-items.md): the use-item's def key is
 * stored as a PDC {@code STRING} under {@link ItemKeys#useItem()}, off the combat hot path. Presence of the
 * key <em>is</em> the identity (mirrors {@link UnopenedBookCodec}); {@link #keyOf} yields the def the feature
 * layer resolves in the library.
 */
public final class UseItemCodec {

    private final String key;
    private final ItemStateStore store;

    public UseItemCodec(String key, ItemStateStore store) {
        this.key = key;
        this.store = store;
    }

    public boolean isUseItem(ItemStack stack) {
        return keyOf(stack) != null;
    }

    /** The use-item def key {@code stack} carries, or {@code null} if it is not a use-item. */
    public String keyOf(ItemStack stack) {
        String raw = store.read(stack, key);
        return raw == null || raw.isBlank() ? null : raw;
    }

    public void stamp(ItemStack stack, String defKey) {
        store.write(stack, key, defKey);
    }
}
