package item.codec;

import org.bukkit.inventory.ItemStack;

/**
 * Marks / reads a MASK head item (ADR-0053). A mask stores its def key as a PDC {@code STRING} under
 * {@link ItemKeys#maskItem()} — presence <em>is</em> the identity, the {@link PetCodec}/{@link UseItemCodec}
 * shape, but with no counters: a mask never levels, so nothing rides its own integer keys. This is the
 * mask <em>item</em>'s identity before it is applied; once applied onto a helmet the key lives in that
 * helmet's combat blob ({@link CombatState#maskKey()}), not here.
 *
 * <p>Pure state, decode-tolerant: an absent/blank read is simply "not a mask", never a throw.
 */
public final class MaskCodec {

    private final String maskKey;
    private final ItemStateStore store;

    public MaskCodec(ItemKeys keys, ItemStateStore store) {
        this.maskKey = keys.maskItem();
        this.store = store;
    }

    public boolean isMask(ItemStack stack) {
        return keyOf(stack) != null;
    }

    /**
     * The mask ENTRY {@code stack} carries, or {@code null} if it is not a mask. A composite's entry is its
     * children joined ({@code "masks/a+masks/b"}, ADR-0074) — the same string a helmet stores, so applying is
     * still a straight copy.
     */
    public String keyOf(ItemStack stack) {
        String raw = store.read(stack, maskKey);
        return raw == null || raw.isBlank() ? null : raw;
    }

    /** The children {@code stack} folds, or {@code null} if it is not a mask. One key for a plain mask. */
    public MaskItemData dataOf(ItemStack stack) {
        String entry = keyOf(stack);
        java.util.List<String> keys = MaskItemData.componentsOf(entry);
        // Absent or malformed (an entry of nothing but delimiters) → not a mask, never a throw.
        return keys.isEmpty() || keys.size() > MaskItemData.ABSOLUTE_MAX ? null : new MaskItemData(keys);
    }

    /** Stamp a mask item's identity (mint / admin give) — a plain key, or a composite's joined entry. */
    public void stamp(ItemStack stack, String defKey) {
        store.write(stack, maskKey, defKey);
    }

    /** Stamp {@code data}'s children as this mask item's identity. */
    public void stamp(ItemStack stack, MaskItemData data) {
        store.write(stack, maskKey, data == null ? null : data.entry());
    }
}
