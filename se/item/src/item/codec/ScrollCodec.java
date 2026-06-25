package item.codec;

import java.util.Locale;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

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
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        String raw = stack.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        return raw == null || raw.isBlank() ? null : raw.toUpperCase(Locale.ROOT);
    }

    public void mark(ItemStack stack, String kind) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, kind.toUpperCase(Locale.ROOT));
        stack.setItemMeta(meta);
    }
}
