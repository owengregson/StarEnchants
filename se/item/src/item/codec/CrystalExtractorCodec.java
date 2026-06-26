package item.codec;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

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
        return ItemFlagStore.hasByte(stack, key);
    }

    public void mark(ItemStack stack) {
        ItemFlagStore.setByte(stack, key, true);
    }
}
