package item.codec;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * The single mutation seam for STRING-payload on-item state (§4.2). Every codec that stores a string
 * blob — combat, soul, crystal, carrier, scroll, unopened-book — reads and writes it here, so the
 * {@code getItemMeta() → PDC → setItemMeta()} round-trip exists in <em>one</em> place rather than as a
 * copied convention in each codec. Codecs own only their key and their blob format; this owns the access.
 *
 * <p>Concrete and stateless by choice: the keys are passed per call (every codec already holds its own
 * {@link NamespacedKey}), so there is nothing to inject across the ~30 codec construction sites. If a
 * second, NBT-backed store is ever needed for a legacy fork, this becomes the modern impl of an extracted
 * interface (docs/legacy-1.8.9-codeshare-design.md §3.1) — not built today (YAGNI).
 */
public final class ItemBlobStore {

    private ItemBlobStore() {
    }

    /** The string stored under {@code key}, or {@code null} if {@code stack} carries none. */
    public static String read(ItemStack stack, NamespacedKey key) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    /** Writes {@code blob} under {@code key}; a {@code null} blob clears the entry. No-op if the item has no meta. */
    public static void write(ItemStack stack, NamespacedKey key, String blob) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (blob == null) {
            pdc.remove(key);
        } else {
            pdc.set(key, PersistentDataType.STRING, blob);
        }
        stack.setItemMeta(meta);
    }
}
