package item.codec;

import java.util.ArrayList;
import java.util.Arrays;
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
 * the "topmost" component the extractor pops (ADR-0034 §4). Crystal keys never contain {@code '+'}, so it is an
 * unambiguous delimiter.
 *
 * @param keys the component crystal base keys (e.g. {@code crystals/flame}); 1..{@link #ABSOLUTE_MAX}, never empty
 */
public record CrystalItemData(List<String> keys) {

    public static final String DELIMITER = "+";

    /** Absolute PDC-bloat ceiling on components in one crystal/entry — a sanity guard above any sane max-merge. */
    public static final int ABSOLUTE_MAX = 16;

    public CrystalItemData {
        keys = List.copyOf(keys);
        if (keys.isEmpty() || keys.size() > ABSOLUTE_MAX) {
            throw new IllegalArgumentException("a crystal item holds 1.." + ABSOLUTE_MAX + " keys, got " + keys.size());
        }
    }

    public static CrystalItemData single(String key) {
        return new CrystalItemData(List.of(key));
    }

    public boolean isMulti() {
        return keys.size() > 1;
    }

    /** The gear crystal-list entry encoding these keys ({@code "a+b+c"}, or just {@code "a"} for a single). */
    public String entry() {
        return String.join(DELIMITER, keys);
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
        int cap = Math.min(maxComponents, ABSOLUTE_MAX);
        if (keys.size() + other.keys.size() > cap) {
            return null;
        }
        List<String> combined = new ArrayList<>(keys.size() + other.keys.size());
        combined.addAll(keys);
        combined.addAll(other.keys);
        return new CrystalItemData(combined);
    }

    /** Split a gear crystal-entry into its component crystal keys (a plain key → singleton). */
    public static List<String> componentsOf(String entry) {
        if (entry == null || entry.isBlank()) {
            return List.of();
        }
        if (entry.indexOf(DELIMITER) < 0) {
            return List.of(entry);
        }
        List<String> out = new ArrayList<>();
        for (String part : Arrays.asList(entry.split("\\+"))) {
            if (!part.isBlank()) {
                out.add(part);
            }
        }
        return out;
    }
}
