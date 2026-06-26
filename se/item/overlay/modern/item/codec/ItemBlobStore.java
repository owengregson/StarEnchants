package item.codec;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Modern (1.14+ PDC) impl of the STRING-payload item-state seam (§4.2, docs/legacy-1.8.9-codeshare-design.md
 * §3.1). The codec layer calls this by <em>logical</em> key; here each maps to a {@code starenchants:}
 * namespaced PDC key. {@code NamespacedKey("starenchants", k)} is byte-identical to the historical
 * {@code NamespacedKey(plugin, k)} (the plugin name lowercases to {@code starenchants}), so items written by
 * any prior build still resolve.
 *
 * <p>One of the two same-FQN overlay variants: the modern jar carries this PDC impl; the 1.8 legacy jar
 * carries the {@code overlay/legacy} NMS-tag impl. Selected at build assembly, never probed at runtime.
 * PDC ({@code org.bukkit.persistence.*}) and {@link NamespacedKey} do not exist on 1.8.9, which is exactly
 * why this lives in the overlay and not in shared {@code main}.
 */
public final class ItemBlobStore {

    private static final String NAMESPACE = "starenchants";

    private ItemBlobStore() {
    }

    /** The string stored under {@code logicalKey}, or {@code null} if {@code stack} carries none. */
    public static String read(ItemStack stack, String logicalKey) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(key(logicalKey), PersistentDataType.STRING);
    }

    /** Writes {@code blob} under {@code logicalKey}; a {@code null} blob clears it. No-op if the item has no meta. */
    public static void write(ItemStack stack, String logicalKey, String blob) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (blob == null) {
            pdc.remove(key(logicalKey));
        } else {
            pdc.set(key(logicalKey), PersistentDataType.STRING, blob);
        }
        stack.setItemMeta(meta);
    }

    private static NamespacedKey key(String logicalKey) {
        // fromString("starenchants:k") == NamespacedKey(plugin, k); the non-deprecated, install-free form.
        return NamespacedKey.fromString(NAMESPACE + ":" + logicalKey);
    }
}
