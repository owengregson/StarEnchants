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
    private final String heroicMinKey;
    private final String heroicMaxKey;
    private final ItemStateStore store;

    /** Convenience: no conversion store (the kind-only scrolls, and tests that never mint a black scroll). */
    public ScrollCodec(String key, ItemStateStore store) {
        this(key, key + "convert", key + "heroicmin_v1", key + "heroicmax_v1", store);
    }

    public ScrollCodec(String key, String convertKey, ItemStateStore store) {
        this(key, convertKey, key + "heroicmin_v1", key + "heroicmax_v1", store);
    }

    public ScrollCodec(String key, String convertKey, String heroicMinKey, String heroicMaxKey,
                       ItemStateStore store) {
        this.key = key;
        this.convertKey = convertKey;
        this.heroicMinKey = heroicMinKey;
        this.heroicMaxKey = heroicMaxKey;
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

    /** Stores the Heroic Black Scroll conversion range as versioned item state, not display lore. */
    public void markHeroicRange(ItemStack stack, int min, int max) {
        Ranges.IntRange range = Ranges.percentRange(min, max);
        store.writeInt(stack, heroicMinKey, range.min());
        store.writeInt(stack, heroicMaxKey, range.max());
    }

    /** Reads a Heroic Black Scroll range; missing or malformed legacy state falls back safely. */
    public Ranges.IntRange heroicRangeOf(ItemStack stack, int fallbackMin, int fallbackMax) {
        int min = store.hasInt(stack, heroicMinKey) ? store.readInt(stack, heroicMinKey, fallbackMin) : fallbackMin;
        int max = store.hasInt(stack, heroicMaxKey) ? store.readInt(stack, heroicMaxKey, fallbackMax) : fallbackMax;
        return Ranges.percentRange(min, max);
    }
}
