package item.codec;

import org.bukkit.inventory.ItemStack;

/**
 * Reads/writes a {@link CrystalItemData} on a physical crystal item (docs/v3-directives.md §E), one PDC
 * {@code STRING} under {@link ItemKeys#crystalItem()} — separate from the {@link CombatCodec} blob (a
 * crystal is identity, off the combat hot path) and from the {@link CarrierCodec} (a crystal is its own
 * item, not a book/scroll). Payload is the component keys joined by {@code '+'} (one for a single, many for
 * a merged multi-crystal). A null/empty entry decodes to {@code null}, never throws.
 */
public final class CrystalItemCodec {

    private final String key;
    private final ItemStateStore store;

    public CrystalItemCodec(String key, ItemStateStore store) {
        this.key = key;
        this.store = store;
    }

    public CrystalItemData read(ItemStack stack) {
        java.util.List<String> keys = CrystalItemData.componentsOf(store.read(stack, key));
        if (keys.isEmpty() || keys.size() > CrystalItemData.ABSOLUTE_MAX) {
            return null; // absent or malformed → treat as not-a-crystal, never throw
        }
        return new CrystalItemData(keys);
    }

    public void write(ItemStack stack, CrystalItemData data) {
        store.write(stack, key, data == null ? null : data.entry());
    }
}
