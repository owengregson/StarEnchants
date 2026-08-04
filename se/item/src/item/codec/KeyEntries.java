package item.codec;

import java.util.ArrayList;
import java.util.List;

/**
 * How N stable keys pack into ONE on-item entry string — the shared arithmetic behind multi-crystals
 * (ADR-0034/0035) and multi-masks (ADR-0074).
 *
 * <p>It lives in one place because the two families must agree on the delimiter and on the singleton rule:
 * a lone key encodes as ITSELF, so every item stamped before either family learned to combine still decodes.
 * Stable keys never contain {@code '+'} (they are {@code family/stem} paths), which is what makes it an
 * unambiguous separator rather than a convention.
 *
 * <p>Order is preserved and load-bearing: the LAST element is the "topmost" component — the most recently
 * merged, and the one an extractor pops first.
 */
final class KeyEntries {

    /** Absolute PDC-bloat ceiling on components in one entry — a sanity guard above any sane configured cap. */
    static final int ABSOLUTE_MAX = 16;

    static final String DELIMITER = "+";

    private KeyEntries() {
    }

    /** The entry string encoding {@code keys} ({@code "a+b+c"}, or just {@code "a"} for a single). */
    static String encode(List<String> keys) {
        return String.join(DELIMITER, keys);
    }

    /** Split an entry into its component keys (a plain key → singleton; blank/null → empty). */
    static List<String> componentsOf(String entry) {
        if (entry == null || entry.isBlank()) {
            return List.of();
        }
        if (entry.indexOf(DELIMITER) < 0) {
            return List.of(entry);
        }
        List<String> out = new ArrayList<>();
        for (String part : entry.split("\\+")) {
            if (!part.isBlank()) {
                out.add(part);
            }
        }
        return out;
    }

    /**
     * {@code base} with {@code added} appended ON TOP (last, so an extractor pops the most recently merged
     * component first), or {@code null} when the combined count would exceed {@code maxComponents} or
     * {@link #ABSOLUTE_MAX}. Overflow is a {@code null}, never an exception: the caller turns it into a refused
     * gesture that spends nothing.
     */
    static List<String> merged(List<String> base, List<String> added, int maxComponents) {
        int cap = Math.min(maxComponents, ABSOLUTE_MAX);
        if (base.size() + added.size() > cap) {
            return null;
        }
        List<String> combined = new ArrayList<>(base.size() + added.size());
        combined.addAll(base);
        combined.addAll(added);
        return combined;
    }

    /** Guard the 1..{@link #ABSOLUTE_MAX} invariant both records' canonical constructors share. */
    static List<String> checked(List<String> keys, String what) {
        List<String> copy = List.copyOf(keys);
        if (copy.isEmpty() || copy.size() > ABSOLUTE_MAX) {
            throw new IllegalArgumentException("a " + what + " holds 1.." + ABSOLUTE_MAX + " keys, got " + copy.size());
        }
        return copy;
    }
}
