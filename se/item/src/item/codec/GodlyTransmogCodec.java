package item.codec;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Marks / detects a physical GODLY TRANSMOG tool (docs/v3-directives.md §I/§K) — dragged onto enchanted
 * gear, it opens the deterministic enchant-reorder GUI bound to that piece (unlike the one-shot transmog
 * SCROLL, which randomly shuffles). A simple PDC {@code BYTE} flag under {@link ItemKeys#godlyTransmog()},
 * separate from the combat blob (identity, never on the hot path) and from the scroll consume path; mirrors
 * {@link CrystalExtractorCodec}. Reading is null-safe.
 */
public final class GodlyTransmogCodec {

    private final NamespacedKey key;

    public GodlyTransmogCodec(NamespacedKey key) {
        this.key = key;
    }

    /** Whether {@code stack} is a godly transmog tool. */
    public boolean isGodlyTransmog(ItemStack stack) {
        return stack != null && stack.hasItemMeta()
                && stack.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    /** Stamp the godly-transmog marker onto {@code stack}. */
    public void mark(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
    }
}
