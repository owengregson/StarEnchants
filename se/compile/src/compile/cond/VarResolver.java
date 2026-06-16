package compile.cond;

import java.util.Optional;

/**
 * The compiler's view of the condition variable vocabulary: resolves a
 * {@code %scope.name%} reference to its {@link VarBinding} (type + dense slot), or
 * empty for an unknown token — which the compiler then treats as a PlaceholderAPI
 * passthrough (docs/architecture.md §3.4).
 *
 * <p>This is the seam that keeps {@code se-compile} pure: the engine declares the
 * vocabulary (which facts exist and which slots they occupy) and injects this facade,
 * exactly as it injects the effect/selector spec registries. The empty vocabulary
 * ({@link #none()}) makes every variable a PlaceholderAPI token.
 */
public interface VarResolver {

    /**
     * Resolve a variable reference. {@code scope} is the part before the first dot
     * (e.g. {@code victim} in {@code %victim.health%}) or {@code null} for a bare
     * {@code %name%}; {@code name} is the remainder.
     */
    Optional<VarBinding> resolve(String scope, String name);

    /** A vocabulary that knows no variables — every reference becomes a PAPI token. */
    static VarResolver none() {
        return (scope, name) -> Optional.empty();
    }
}
