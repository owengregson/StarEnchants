/**
 * The on-item PDC codec (docs/architecture.md §4.2, §5.1): one compact, stable-string-keyed,
 * version-tagged record of an item's STATE — never its behavior. {@link item.codec.CombatState}
 * is the combat-relevant identity (enchant keys&rarr;levels, crystal key list) decoded on the hot
 * path; {@link item.codec.CombatCodec} serialises it to a single PDC entry (one read, one decode)
 * and adapts that to {@code ItemStack}/{@code PersistentDataContainer}; {@link item.codec.ItemKeys}
 * owns the versioned {@code NamespacedKey}s. The pure blob format is unit-tested; the PDC round-trip
 * across the mapping flip is verified live (§11).
 */
package item.codec;
