package item.codec;

import org.bukkit.inventory.ItemStack;

/**
 * Marks / reads a REFORGE catalogue item (ADR-0070). A reforge item stores its def key as a PDC
 * {@code STRING} under {@link ItemKeys#reforgeItem()} — presence <em>is</em> the identity, the
 * {@link MaskCodec} shape, no counters. This is the reforge <em>item</em>'s identity before it is
 * applied; once applied onto a weapon the key lives in that weapon's combat blob
 * ({@link CombatState#reforgeKey()}), not here.
 *
 * <p>Pure state, decode-tolerant: an absent/blank read is simply "not a reforge", never a throw.
 */
public final class ReforgeCodec {

    private final String reforgeKey;
    private final ItemStateStore store;

    public ReforgeCodec(ItemKeys keys, ItemStateStore store) {
        this.reforgeKey = keys.reforgeItem();
        this.store = store;
    }

    public boolean isReforge(ItemStack stack) {
        return keyOf(stack) != null;
    }

    /** The reforge def key {@code stack} carries, or {@code null} if it is not a reforge item. */
    public String keyOf(ItemStack stack) {
        String raw = store.read(stack, reforgeKey);
        return raw == null || raw.isBlank() ? null : raw;
    }

    /** Stamp a reforge item's identity (mint / admin give). */
    public void stamp(ItemStack stack, String defKey) {
        store.write(stack, reforgeKey, defKey);
    }
}
