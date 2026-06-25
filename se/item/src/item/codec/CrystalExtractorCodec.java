package item.codec;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Marks / detects a crystal EXTRACTOR item (docs/v3-directives.md §E). A PDC {@code BYTE} flag under
 * {@link ItemKeys#crystalExtractor()}, kept off the combat blob (identity, never on the hot path); mirrors
 * {@link HeroicUpgradeCodec}.
 */
public final class CrystalExtractorCodec {

    private final NamespacedKey key;

    public CrystalExtractorCodec(NamespacedKey key) {
        this.key = key;
    }

    public boolean isExtractor(ItemStack stack) {
        return stack != null && stack.hasItemMeta()
                && stack.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    public void mark(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
    }
}
