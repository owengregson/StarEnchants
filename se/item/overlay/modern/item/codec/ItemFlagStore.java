package item.codec;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Modern (PDC) impl of the BYTE/INTEGER marker seam (§4.2) — the guarded flag, the crystal-extractor /
 * godly-transmog / heroic-upgrade marks, and the slot-orb {@code +N}. Counterpart to {@link ItemBlobStore};
 * the 1.8 legacy jar carries the same-FQN NMS-tag impl from {@code overlay/legacy}.
 */
public final class ItemFlagStore {

    private static final String NAMESPACE = "starenchants";

    private ItemFlagStore() {
    }

    /** Whether {@code stack} carries the BYTE flag under {@code logicalKey}. */
    public static boolean hasByte(ItemStack stack, String logicalKey) {
        return stack != null && stack.hasItemMeta()
                && stack.getItemMeta().getPersistentDataContainer().has(key(logicalKey), PersistentDataType.BYTE);
    }

    /** Sets ({@code 1}) or clears the BYTE flag under {@code logicalKey}. No-op if the item has no meta. */
    public static void setByte(ItemStack stack, String logicalKey, boolean set) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (set) {
            pdc.set(key(logicalKey), PersistentDataType.BYTE, (byte) 1);
        } else {
            pdc.remove(key(logicalKey));
        }
        stack.setItemMeta(meta);
    }

    /** Whether {@code stack} carries an INTEGER under {@code logicalKey}. */
    public static boolean hasInt(ItemStack stack, String logicalKey) {
        return stack != null && stack.hasItemMeta()
                && stack.getItemMeta().getPersistentDataContainer().has(key(logicalKey), PersistentDataType.INTEGER);
    }

    /** The INTEGER under {@code logicalKey}, or {@code dflt} if absent. */
    public static int readInt(ItemStack stack, String logicalKey, int dflt) {
        if (stack == null || !stack.hasItemMeta()) {
            return dflt;
        }
        Integer value = stack.getItemMeta().getPersistentDataContainer().get(key(logicalKey), PersistentDataType.INTEGER);
        return value == null ? dflt : value;
    }

    /** Writes {@code value} as an INTEGER under {@code logicalKey}. No-op if the item has no meta. */
    public static void writeInt(ItemStack stack, String logicalKey, int value) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(key(logicalKey), PersistentDataType.INTEGER, value);
        stack.setItemMeta(meta);
    }

    private static NamespacedKey key(String logicalKey) {
        // fromString("starenchants:k") == NamespacedKey(plugin, k); the non-deprecated, install-free form.
        return NamespacedKey.fromString(NAMESPACE + ":" + logicalKey);
    }
}
