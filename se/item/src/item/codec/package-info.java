/**
 * On-item PDC codec (§4.2, §5.1): a compact, stable-string-keyed, version-tagged record of item STATE,
 * never behavior. {@link item.codec.CombatState} is the hot-path combat identity (enchant
 * keys&rarr;levels, crystal keys); {@link item.codec.CombatCodec} serialises it to one PDC entry (one
 * read, one decode); {@link item.codec.ItemKeys} owns the versioned keys. The PDC round-trip across the
 * 1.20.5 mapping flip is verified live (§11).
 */
package item.codec;
