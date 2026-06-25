package item.codec;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

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
