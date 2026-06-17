package item.codec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A physical crystal item's on-item state (docs/v3-directives.md §E): the ordered list of component
 * crystal base keys it carries — ONE for a single crystal, TWO for a merged multi-crystal (pairs only).
 * Distinct from the book/scroll/dust {@link CarrierData} economy: a crystal is its own item, drag-applied
 * to gear with a success roll, and two crystals merge into a multi-crystal.
 *
 * <p>When applied, the keys become ONE entry in a gear's {@link CombatState#crystals()} list, encoded
 * {@code "a+b"} (a single crystal is just the plain key, so legacy single-key entries stay valid). The
 * entry occupies ONE crystal slot but contributes BOTH abilities; the runtime additive fold (ADR-0012)
 * sums overlapping effect magnitudes, which is the multi-crystal "overlapping types SUM" semantics for
 * free. Crystal keys never contain {@code '+'}, so it is an unambiguous delimiter.
 *
 * @param keys the component crystal base keys (e.g. {@code crystals/jolt}); 1 or 2, never empty/null
 */
public record CrystalItemData(List<String> keys) {

    /** The delimiter joining a multi-crystal's component keys within a single gear crystal-entry. */
    public static final String DELIMITER = "+";

    /** The maximum components a crystal item may hold — multi-crystals are PAIRS only (§E). */
    public static final int MAX_COMPONENTS = 2;

    public CrystalItemData {
        keys = List.copyOf(keys);
        if (keys.isEmpty() || keys.size() > MAX_COMPONENTS) {
            throw new IllegalArgumentException("a crystal item holds 1.." + MAX_COMPONENTS + " keys, got " + keys.size());
        }
    }

    /** A single-crystal item. */
    public static CrystalItemData single(String key) {
        return new CrystalItemData(List.of(key));
    }

    /** Whether this is a merged multi-crystal (two components). */
    public boolean isMulti() {
        return keys.size() == MAX_COMPONENTS;
    }

    /** The gear crystal-list ENTRY encoding these keys ({@code "a+b"}, or just {@code "a"} for a single). */
    public String entry() {
        return String.join(DELIMITER, keys);
    }

    /**
     * Merge this crystal with {@code other} into a multi-crystal — pairs only, so both must be singles
     * (an already-merged crystal cannot merge further). Returns {@code null} when the merge is illegal.
     */
    public CrystalItemData mergeWith(CrystalItemData other) {
        if (other == null || isMulti() || other.isMulti()) {
            return null;
        }
        return new CrystalItemData(List.of(keys.get(0), other.keys.get(0)));
    }

    /** Split a gear crystal-entry into its component crystal keys (a plain key → singleton). */
    public static List<String> componentsOf(String entry) {
        if (entry == null || entry.isBlank()) {
            return List.of();
        }
        if (entry.indexOf(DELIMITER) < 0) {
            return List.of(entry);
        }
        List<String> out = new ArrayList<>(MAX_COMPONENTS);
        for (String part : Arrays.asList(entry.split("\\+"))) {
            if (!part.isBlank()) {
                out.add(part);
            }
        }
        return out;
    }
}
