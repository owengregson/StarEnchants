package item.head;

import item.codec.ItemKeys;
import item.codec.ItemStateStore;
import java.util.Base64;
import java.util.Objects;
import org.bukkit.inventory.ItemStack;

/**
 * Marks a mask-illusion shown head as NOT-A-REAL-ITEM and carries the real helmet for lossless recovery
 * (ADR-0064). A shown head only ever exists inside equipment packets; if a client writes one back into a
 * real inventory (the creative-mode echo), the marker makes it detectable everywhere and {@link #undress}
 * restores the exact helmet it was dressed from. A payload-less mark (byte codec unavailable) still
 * detects — callers then deny instead of repairing.
 */
public final class IllusionMark {

    /** Payload sentinel for "marked, no byte codec": detection works, {@link #undress} yields null. */
    private static final String NO_PAYLOAD = "-";

    private final String key;
    private final ItemStateStore store;
    private final ItemBytes bytes;

    public IllusionMark(ItemKeys keys, ItemStateStore store, ItemBytes bytes) {
        this.key = Objects.requireNonNull(keys, "keys").illusion();
        this.store = Objects.requireNonNull(store, "store");
        this.bytes = Objects.requireNonNull(bytes, "bytes");
    }

    /** Stamp {@code shownHead} as the illusion of {@code realHelmet} (with payload when the codec allows). */
    public void stamp(ItemStack shownHead, ItemStack realHelmet) {
        byte[] encoded = bytes.serialize(realHelmet);
        store.write(shownHead, key, encoded == null ? NO_PAYLOAD : Base64.getEncoder().encodeToString(encoded));
    }

    public boolean isMarked(ItemStack stack) {
        return stack != null && store.read(stack, key) != null;
    }

    /** The real helmet this marked head was dressed from, or {@code null} (unmarked / no payload / corrupt). */
    public ItemStack undress(ItemStack stack) {
        String payload = stack == null ? null : store.read(stack, key);
        if (payload == null || NO_PAYLOAD.equals(payload)) {
            return null;
        }
        try {
            return bytes.deserialize(Base64.getDecoder().decode(payload));
        } catch (IllegalArgumentException corrupt) {
            return null; // a forged/corrupt payload never throws on the click path
        }
    }
}
