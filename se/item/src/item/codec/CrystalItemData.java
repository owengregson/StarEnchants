package item.codec;

import java.util.List;

/**
 * A physical crystal item's on-item state (docs/v3-directives.md §E, ADR-0034): the ordered list of component
 * crystal base keys it carries — ONE for a single crystal, MANY for a merged multi-crystal. The <em>runtime</em>
 * cap on how many may merge is {@code crystals.max-merge} (config, enforced by the feature layer); this record
 * only guards the absolute {@link #ABSOLUTE_MAX} that keeps the PDC string bounded.
 *
 * <p>Applied, the keys become ONE entry in a gear's {@link CombatState#crystals()} list, encoded {@code "a+b+c"}
 * (a single is the plain key, so legacy single-key entries stay valid). The entry occupies one slot but
 * contributes every component's abilities; the runtime additive fold (ADR-0012) sums overlapping magnitudes — the
 * multi-crystal "overlapping types SUM" semantics for free. The list is ORDER-PRESERVING and the last element is
 * the "topmost" component the extractor pops (ADR-0034 §4). The packing itself is {@link KeyEntries}, shared with
 * the mask composite (ADR-0074) so the two families cannot drift on the delimiter.
 *
 * @param keys the component crystal base keys (e.g. {@code crystals/flame}); 1..{@link #ABSOLUTE_MAX}, never empty
 */
public record CrystalItemData(List<String> keys) {

    public static final String DELIMITER = KeyEntries.DELIMITER;

    /** Absolute PDC-bloat ceiling on components in one crystal/entry — a sanity guard above any sane max-merge. */
    public static final int ABSOLUTE_MAX = KeyEntries.ABSOLUTE_MAX;

    public CrystalItemData {
        keys = KeyEntries.checked(keys, "crystal item");
    }

    public static CrystalItemData single(String key) {
        return new CrystalItemData(List.of(key));
    }

    public boolean isMulti() {
        return keys.size() > 1;
    }

    /** The gear crystal-list entry encoding these keys ({@code "a+b+c"}, or just {@code "a"} for a single). */
    public String entry() {
        return KeyEntries.encode(keys);
    }

    /**
     * Merge {@code other} onto this crystal into a multi-crystal, {@code other}'s components landing ON TOP
     * (last, so the extractor pops the most-recently-merged crystal first, §4). Rejected — returns {@code null} —
     * when the combined component count would exceed {@code maxComponents} (the {@code crystals.max-merge} cap)
     * or the absolute {@link #ABSOLUTE_MAX}.
     */
    public CrystalItemData mergeWith(CrystalItemData other, int maxComponents) {
        if (other == null) {
            return null;
        }
        List<String> combined = KeyEntries.merged(keys, other.keys, maxComponents);
        return combined == null ? null : new CrystalItemData(combined);
    }

    /** Split a gear crystal-entry into its component crystal keys (a plain key → singleton). */
    public static List<String> componentsOf(String entry) {
        return KeyEntries.componentsOf(entry);
    }
}
