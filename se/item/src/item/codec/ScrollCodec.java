package item.codec;

import java.util.Locale;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Tags / detects a SCROLL by its kind (docs/v3-directives.md §I) — a one-shot consumable whose behaviour
 * is decided by its kind ({@code BLACK} extract-to-book, {@code RANDOMIZER} reroll-book-success, and later
 * {@code TRANSMOG} / {@code NAMETAG} / {@code HOLY}). The kind is a PDC {@code STRING} under
 * {@link ItemKeys#scroll()}, separate from the combat blob (identity, never on the hot path). The scroll's
 * mechanics/likeness come from the scrolls config, not the item. Reading is null-safe; an unrecognised kind
 * reads as {@code null}.
 */
public final class ScrollCodec {

    private final NamespacedKey key;

    public ScrollCodec(NamespacedKey key) {
        this.key = key;
    }

    /** Whether {@code stack} is any scroll. */
    public boolean isScroll(ItemStack stack) {
        return kind(stack) != null;
    }

    /** The scroll kind on {@code stack} (upper-cased), or {@code null} if it is not a scroll. */
    public String kind(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        String raw = stack.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        return raw == null || raw.isBlank() ? null : raw.toUpperCase(Locale.ROOT);
    }

    /** Stamp the scroll kind onto {@code stack}. */
    public void mark(ItemStack stack, String kind) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, kind.toUpperCase(Locale.ROOT));
        stack.setItemMeta(meta);
    }
}
