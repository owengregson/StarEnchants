package item.codec;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

/**
 * Reads/writes a {@link CrystalItemData} on a physical crystal item (docs/v3-directives.md §E), one PDC
 * {@code STRING} under {@link ItemKeys#crystalItem()} — separate from the {@link CombatCodec} blob (a
 * crystal is identity, off the combat hot path) and from the {@link CarrierCodec} (a crystal is its own
 * item, not a book/scroll). Payload is the component keys joined by {@code '+'} (one for a single, two for
 * a multi). A null/empty entry decodes to {@code null}, never throws.
 */
public final class CrystalItemCodec {

    private final NamespacedKey key;

    public CrystalItemCodec(NamespacedKey key) {
        this.key = key;
    }

    public CrystalItemData read(ItemStack stack) {
        java.util.List<String> keys = CrystalItemData.componentsOf(ItemBlobStore.read(stack, key));
        if (keys.isEmpty() || keys.size() > CrystalItemData.MAX_COMPONENTS) {
            return null; // absent or malformed → treat as not-a-crystal, never throw
        }
        return new CrystalItemData(keys);
    }

    public void write(ItemStack stack, CrystalItemData data) {
        ItemBlobStore.write(stack, key, data == null ? null : data.entry());
    }
}
