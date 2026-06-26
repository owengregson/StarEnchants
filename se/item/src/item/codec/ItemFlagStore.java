package item.codec;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * The single mutation seam for the small BYTE/INTEGER markers (§4.2) — the guarded flag, the crystal
 * extractor / godly-transmog / heroic-upgrade marks, and the slot-orb {@code +N}. Counterpart to
 * {@link ItemBlobStore}, split out because these are mint-time identity markers (never the combat hot
 * path) carrying a primitive rather than a string blob; primitive in/out so there is no boxing even if a
 * marker is ever read on a path.
 *
 * <p>Concrete and stateless by the same reasoning as {@link ItemBlobStore}.
 */
public final class ItemFlagStore {

    private ItemFlagStore() {
    }

    /** Whether {@code stack} carries the BYTE flag under {@code key}. */
    public static boolean hasByte(ItemStack stack, NamespacedKey key) {
        return stack != null && stack.hasItemMeta()
                && stack.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    /** Sets ({@code 1}) or clears the BYTE flag under {@code key}. No-op if the item has no meta. */
    public static void setByte(ItemStack stack, NamespacedKey key, boolean set) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (set) {
            pdc.set(key, PersistentDataType.BYTE, (byte) 1);
        } else {
            pdc.remove(key);
        }
        stack.setItemMeta(meta);
    }

    /** Whether {@code stack} carries an INTEGER under {@code key}. */
    public static boolean hasInt(ItemStack stack, NamespacedKey key) {
        return stack != null && stack.hasItemMeta()
                && stack.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.INTEGER);
    }

    /** The INTEGER under {@code key}, or {@code dflt} if absent. */
    public static int readInt(ItemStack stack, NamespacedKey key, int dflt) {
        if (stack == null || !stack.hasItemMeta()) {
            return dflt;
        }
        Integer value = stack.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
        return value == null ? dflt : value;
    }

    /** Writes {@code value} as an INTEGER under {@code key}. No-op if the item has no meta. */
    public static void writeInt(ItemStack stack, NamespacedKey key, int value) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, value);
        stack.setItemMeta(meta);
    }
}
