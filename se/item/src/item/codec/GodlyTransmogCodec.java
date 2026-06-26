package item.codec;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

/**
 * Marks / detects a physical GODLY TRANSMOG tool (§I/§K): a PDC {@code BYTE} flag under
 * {@link ItemKeys#godlyTransmog()}, off both the combat hot path and the scroll consume path
 * (it opens a reorder GUI rather than being consumed). Mirrors {@link CrystalExtractorCodec}.
 */
public final class GodlyTransmogCodec {

    private final NamespacedKey key;

    public GodlyTransmogCodec(NamespacedKey key) {
        this.key = key;
    }

    public boolean isGodlyTransmog(ItemStack stack) {
        return ItemFlagStore.hasByte(stack, key);
    }

    public void mark(ItemStack stack) {
        ItemFlagStore.setByte(stack, key, true);
    }
}
