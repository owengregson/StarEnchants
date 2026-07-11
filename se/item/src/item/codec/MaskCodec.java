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

    /** The mask def key {@code stack} carries, or {@code null} if it is not a mask. */
    public String keyOf(ItemStack stack) {
        String raw = store.read(stack, maskKey);
        return raw == null || raw.isBlank() ? null : raw;
    }

    /** Stamp a mask item's identity (mint / admin give). */
    public void stamp(ItemStack stack, String defKey) {
        store.write(stack, maskKey, defKey);
    }
}
