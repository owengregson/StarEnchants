package item.codec;

import java.util.Locale;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

/**
 * Tags / detects a SCROLL by kind (§I): a PDC {@code STRING} under {@link ItemKeys#scroll()}, off the
 * combat hot path. Mechanics come from the scrolls config, not the item.
 */
public final class ScrollCodec {

    private final NamespacedKey key;

    public ScrollCodec(NamespacedKey key) {
        this.key = key;
    }

    public boolean isScroll(ItemStack stack) {
        return kind(stack) != null;
    }

    /** The scroll kind on {@code stack}, upper-cased, or {@code null} if it is not a scroll. */
    public String kind(ItemStack stack) {
        String raw = ItemBlobStore.read(stack, key);
        return raw == null || raw.isBlank() ? null : raw.toUpperCase(Locale.ROOT);
    }

    public void mark(ItemStack stack, String kind) {
        ItemBlobStore.write(stack, key, kind.toUpperCase(Locale.ROOT));
    }
}
