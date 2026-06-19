package item.codec;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Marks / detects a crystal EXTRACTOR item (docs/v3-directives.md §E) — a one-shot consumable dragged onto
 * crystal-bearing gear to pop its last crystal back into a whole physical crystal. A simple PDC {@code BYTE}
 * flag under {@link ItemKeys#crystalExtractor()}, separate from the combat blob (identity, never on the hot
 * path); mirrors {@link HeroicUpgradeCodec}. Reading is null-safe.
 */
public final class CrystalExtractorCodec {

    private final NamespacedKey key;

    public CrystalExtractorCodec(NamespacedKey key) {
        this.key = key;
    }

    /** Whether {@code stack} is a crystal extractor item. */
    public boolean isExtractor(ItemStack stack) {
        return stack != null && stack.hasItemMeta()
                && stack.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    /** Stamp the extractor marker onto {@code stack}. */
    public void mark(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
    }
}
