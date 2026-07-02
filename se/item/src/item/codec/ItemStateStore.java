package item.codec;

import org.bukkit.inventory.ItemStack;

/**
 * The one physical item-data layer (§4.2, docs/legacy-1.8.9-codeshare-design.md §3.1): every codec reads and
 * writes on-item state through this seam, by <em>logical</em> key, blind to how the platform stores it. Two
 * era-exclusive impls back it — {@code PdcItemStateStore} (modern 1.14+ PDC, {@code overlay/modern}) and
 * {@code NbtItemStateStore} (legacy 1.8.9 NMS tags, {@code overlay/legacy}) — selected by the composition root
 * and injected into each codec. PDC ({@code NamespacedKey}, {@code PersistentDataContainer}) does not exist on
 * 1.8.9, so the store form is the version edge; the logical-key surface here is version-agnostic (ADR-0044).
 *
 * <p>Combines the string payload (blob) and the byte/integer markers (flag) the old {@code ItemBlobStore} /
 * {@code ItemFlagStore} same-FQN twins split — both are "the physical item-data layer", so one seam suffices.
 * A {@code test}-side {@code FakeItemStateStore} (map-backed) makes the codecs unit-testable without a server.
 */
public interface ItemStateStore {

    /** The string stored under {@code logicalKey}, or {@code null} if {@code stack} carries none. */
    String read(ItemStack stack, String logicalKey);

    /** Writes {@code blob} under {@code logicalKey}; a {@code null} blob clears it. No-op if the item has no meta. */
    void write(ItemStack stack, String logicalKey, String blob);

    /** Whether {@code stack} carries the BYTE flag under {@code logicalKey}. */
    boolean hasByte(ItemStack stack, String logicalKey);

    /** Sets ({@code 1}) or clears the BYTE flag under {@code logicalKey}. No-op if the item has no meta. */
    void setByte(ItemStack stack, String logicalKey, boolean set);

    /** Whether {@code stack} carries an INTEGER under {@code logicalKey}. */
    boolean hasInt(ItemStack stack, String logicalKey);

    /** The INTEGER under {@code logicalKey}, or {@code dflt} if absent. */
    int readInt(ItemStack stack, String logicalKey, int dflt);

    /** Writes {@code value} as an INTEGER under {@code logicalKey}. No-op if the item has no meta. */
    void writeInt(ItemStack stack, String logicalKey, int value);
}
