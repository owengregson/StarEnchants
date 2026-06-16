package platform.resolve;

import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The pure cross-version name-resolution strategy (docs/architecture.md §9). Given an
 * authored token, the per-category legacy&rarr;modern alias map, and an {@code exists}
 * predicate (does this canonical name resolve on the running server version — the
 * Registry/{@code valueOf} check in production, a fixed set in tests), it returns the
 * canonical name that actually exists, or empty (the caller then warn-and-skips that one
 * op; the load never crashes).
 *
 * <p>Resolution is bidirectional so content survives in both directions across the
 * 9-year range:
 * <ol>
 *   <li>the token itself, if it already exists ({@code NAUSEA} on a modern server);</li>
 *   <li>its modern form via the alias map, if the token is a legacy name
 *       ({@code CONFUSION}&rarr;{@code NAUSEA} on a modern server);</li>
 *   <li>its legacy form, if the token is a modern name on an older server
 *       ({@code NAUSEA}&rarr;{@code CONFUSION} on a pre-rename server).</li>
 * </ol>
 *
 * <p>This is the only place the rename knowledge lives; the runtime sees only the
 * resolved handle, never a renamed constant.
 */
public final class HandleResolver {

    private HandleResolver() {
    }

    /**
     * Resolve {@code token} to a canonical name that exists, trying the token, then its
     * forward (legacy&rarr;modern) alias, then its reverse (modern&rarr;legacy) alias.
     *
     * @param token   the authored token (case-insensitive)
     * @param aliases legacy&rarr;modern names for this category
     * @param exists  whether a canonical name resolves on the target server version
     * @return the resolved canonical name, or empty if none of the forms exist
     */
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
