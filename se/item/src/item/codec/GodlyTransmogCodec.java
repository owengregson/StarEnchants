package item.codec;

import org.bukkit.inventory.ItemStack;

/**
 * Marks / detects a physical GODLY TRANSMOG tool (§I/§K): a PDC {@code BYTE} flag under
 * {@link ItemKeys#godlyTransmog()}, off both the combat hot path and the scroll consume path
 * (it opens a reorder GUI rather than being consumed). Mirrors {@link CrystalExtractorCodec}.
 */
public final class GodlyTransmogCodec {

    private final String key;
    private final ItemStateStore store;

    public GodlyTransmogCodec(String key, ItemStateStore store) {
        this.key = key;
        this.store = store;
    }

    public boolean isGodlyTransmog(ItemStack stack) {
        return store.hasByte(stack, key);
    }

    public void mark(ItemStack stack) {
        store.setByte(stack, key, true);
    }
}
