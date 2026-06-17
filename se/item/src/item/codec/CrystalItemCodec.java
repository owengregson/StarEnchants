package item.codec;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Reads and writes a {@link CrystalItemData} on a physical crystal item (docs/v3-directives.md §E),
 * stored as one PDC {@code STRING} under {@link ItemKeys#crystalItem()} — separate from the
 * {@link CombatCodec} blob (a crystal item is identity, never decoded on the combat hot path) and from
 * the {@link CarrierCodec} (a crystal is its own item, not a book/scroll). The payload is the component
 * keys joined by {@code '+'} (one key for a single, two for a multi-crystal). A null/empty entry decodes
 * to {@code null} (not a crystal), never an exception.
 */
public final class CrystalItemCodec {

    private final NamespacedKey key;

    public CrystalItemCodec(NamespacedKey key) {
        this.key = key;
    }

    /** The crystal-item state on {@code stack}, or {@code null} if it carries none. */
    public CrystalItemData read(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        String raw = stack.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        java.util.List<String> keys = CrystalItemData.componentsOf(raw);
        if (keys.isEmpty() || keys.size() > CrystalItemData.MAX_COMPONENTS) {
            return null; // absent or malformed → treat as not-a-crystal, never throw
        }
        return new CrystalItemData(keys);
    }

    /** Write {@code data} onto {@code stack} (clearing the entry when {@code null}). */
    public void write(ItemStack stack, CrystalItemData data) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (data == null) {
            pdc.remove(key);
        } else {
            pdc.set(key, PersistentDataType.STRING, data.entry());
        }
        stack.setItemMeta(meta);
    }
}
