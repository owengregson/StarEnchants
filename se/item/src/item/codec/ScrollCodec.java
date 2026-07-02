package item.codec;

import java.util.Locale;
import org.bukkit.inventory.ItemStack;
import schema.spec.Ranges;

/**
 * Tags / detects a SCROLL by kind (§I): a PDC {@code STRING} under {@link ItemKeys#scroll()}, off the
 * combat hot path. Mechanics come from the scrolls config, not the item.
 *
 * <p>A black scroll additionally carries its rolled new-book CONVERSION success rate as a PDC {@code INTEGER}
 * under {@link ItemKeys#scrollConvert()} — rolled (or fixed) when the scroll is minted so its lore can show
 * the rate, and applied to the book it draws off the gear. Absent on every other scroll kind.
 */
public final class ScrollCodec {

    private final String key;
    private final String convertKey;
    private final ItemStateStore store;

    /** Convenience: no conversion store (the kind-only scrolls, and tests that never mint a black scroll). */
    public ScrollCodec(String key, ItemStateStore store) {
        this(key, key + "convert", store);
    }

    public ScrollCodec(String key, String convertKey, ItemStateStore store) {
        this.key = key;
        this.convertKey = convertKey;
        this.store = store;
    }

    public boolean isScroll(ItemStack stack) {
        return kind(stack) != null;
    }

    /** The scroll kind on {@code stack}, upper-cased, or {@code null} if it is not a scroll. */
    public String kind(ItemStack stack) {
        String raw = store.read(stack, key);
        return raw == null || raw.isBlank() ? null : raw.toUpperCase(Locale.ROOT);
    }

    public void mark(ItemStack stack, String kind) {
        store.write(stack, key, kind.toUpperCase(Locale.ROOT));
    }

    /** Whether {@code stack} carries a stored conversion success rate (a minted black scroll). */
    public boolean hasConvert(ItemStack stack) {
        return store.hasInt(stack, convertKey);
    }

    /** The black scroll's stored new-book conversion success rate (0–100), or {@code fallback} if absent. */
    public int convertOf(ItemStack stack, int fallback) {
        return hasConvert(stack)
                ? Ranges.clampPercent(store.readInt(stack, convertKey, fallback))
                : fallback;
    }

    /** Stamp the black scroll's rolled conversion success rate (clamped {@code [0, 100]}). */
    public void markConvert(ItemStack stack, int percent) {
        store.writeInt(stack, convertKey, Ranges.clampPercent(percent));
    }
}
