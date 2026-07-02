package item.codec;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Modern (1.14+ PDC) impl of {@link ItemStateStore} — the era-exclusive {@code overlay/modern} half of the
 * item-data seam (ADR-0044; §4.2, docs/legacy-1.8.9-codeshare-design.md §3.1). Each logical key maps to a
 * {@code starenchants:}-namespaced PDC key. {@code NamespacedKey.fromString("starenchants:k")} is byte-identical
 * to the historical {@code NamespacedKey(plugin, k)} (the plugin name lowercases to {@code starenchants}), so
 * items written by any prior build still resolve. PDC ({@code org.bukkit.persistence.*}) and {@link NamespacedKey}
 * do not exist on 1.8.9 — precisely why this is an overlay class and not shared {@code main}.
 */
public final class PdcItemStateStore implements ItemStateStore {

    private static final String NAMESPACE = "starenchants";

    @Override
    public String read(ItemStack stack, String logicalKey) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(key(logicalKey), PersistentDataType.STRING);
    }

    @Override
    public void write(ItemStack stack, String logicalKey, String blob) {
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

    @Override
    public boolean hasByte(ItemStack stack, String logicalKey) {
        return stack != null && stack.hasItemMeta()
                && stack.getItemMeta().getPersistentDataContainer().has(key(logicalKey), PersistentDataType.BYTE);
    }

    @Override
    public void setByte(ItemStack stack, String logicalKey, boolean set) {
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

    @Override
    public boolean hasInt(ItemStack stack, String logicalKey) {
        return stack != null && stack.hasItemMeta()
                && stack.getItemMeta().getPersistentDataContainer().has(key(logicalKey), PersistentDataType.INTEGER);
    }

    @Override
    public int readInt(ItemStack stack, String logicalKey, int dflt) {
        if (stack == null || !stack.hasItemMeta()) {
            return dflt;
        }
        Integer value = stack.getItemMeta().getPersistentDataContainer().get(key(logicalKey), PersistentDataType.INTEGER);
        return value == null ? dflt : value;
    }

    @Override
    public void writeInt(ItemStack stack, String logicalKey, int value) {
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
