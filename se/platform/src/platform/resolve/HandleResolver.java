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
        for (Map.Entry<String, String> entry : aliases.entrySet()) {
            if (entry.getValue().equals(norm) && exists.test(entry.getKey())) {
                return Optional.of(entry.getKey()); // modern token on an older server
            }
        }
        return Optional.empty();
    }
}
