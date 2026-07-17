package item.head;

import org.bukkit.inventory.ItemStack;

/**
 * Whole-ItemStack byte serialization (ADR-0064) — an ADR-0044 era seam backing the mask-illusion marker's
 * lossless payload: modern probes Paper's {@code ItemStack#serializeAsBytes}, 1.8 uses the NMS NBT stream.
 * A {@code null} in either direction means "unsupported/corrupt here" and MUST degrade to detection-only
 * behaviour, never a throw.
 */
public interface ItemBytes {

    /** The inert default (tests, exotic servers): marker payloads degrade to detection-only. */
    ItemBytes NONE = new ItemBytes() {
        @Override public byte[] serialize(ItemStack stack) {
            return null;
        }

        @Override public ItemStack deserialize(byte[] bytes) {
            return null;
        }
    };

    /** {@code stack} as version-portable bytes, or {@code null} when this server cannot serialize. */
    byte[] serialize(ItemStack stack);

    /** The stack encoded by {@link #serialize}, or {@code null} on absence/corruption — never a throw. */
    ItemStack deserialize(byte[] bytes);
}
