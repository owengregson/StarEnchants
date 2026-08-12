package platform.resolve;

import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The pure cross-version name-resolution strategy (docs/architecture.md §9). The {@code exists} predicate
 * is the live Registry/{@code valueOf} check in production, a fixed set in tests; a token that resolves to
 * nothing is empty (the caller warn-and-skips, never crashes). Bidirectional, so content survives both ways:
 * <ol>
 *   <li>the token itself ({@code NAUSEA} on a modern server);</li>
 *   <li>its modern form via the alias map ({@code CONFUSION}&rarr;{@code NAUSEA});</li>
 *   <li>its legacy form, for a modern token on an older server ({@code NAUSEA}&rarr;{@code CONFUSION}).</li>
 * </ol>
 */
public final class HandleResolver {

    private HandleResolver() {
    }

    public static Optional<String> resolve(String token, Map<String, String> aliases,
                                           Predicate<String> exists) {
        if (token == null) {
            return Optional.empty();
        }
        String norm = token.trim().toUpperCase(java.util.Locale.ROOT);
        if (norm.isEmpty()) {
            return Optional.empty();
        }
        if (exists.test(norm)) {
            return Optional.of(norm);
        }
        String forward = aliases.get(norm);
        if (forward != null && exists.test(forward)) {
            return Optional.of(forward);
        }
        // Modern token on an older server. Several tables map two legacy keys onto one modern name
        // (BLOCK_CRACK/BLOCK_DUST → BLOCK), and Map.ofEntries' iteration order is salted per JVM start, so the
        // LOWEST matching key wins rather than whichever came first — otherwise one authored token picks a
        // different particle across restarts.
        String legacy = null;
        for (Map.Entry<String, String> entry : aliases.entrySet()) {
            String key = entry.getKey();
            if (entry.getValue().equals(norm) && (legacy == null || key.compareTo(legacy) < 0)
                    && exists.test(key)) {
                legacy = key;
            }
        }
        return Optional.ofNullable(legacy);
    }
}
