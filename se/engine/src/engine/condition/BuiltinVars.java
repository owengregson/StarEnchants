package engine.condition;

/**
 * The explicit, greppable vocabulary of built-in condition variables
 * (docs/architecture.md §3.4 — {@code actorHealth}, {@code targetHealth},
 * {@code damage}, {@code combo}, and the {@code is_sneaking/blocking/flying} flags).
 * Adding a variable is one line here plus its population at activation; the slot is
 * assigned automatically.
 *
 * <p>Variables are written {@code %scope.name%} in conditions; {@code actor} and
 * {@code victim} are the two combat sides, and a bare name (e.g. {@code %sneaking%})
 * refers to the activator.
 */
public final class BuiltinVars {

    private BuiltinVars() {
    }

    /** The vocabulary of all built-in condition variables. */
    public static VarVocabulary vocabulary() {
        return VarVocabulary.builder()
                // Numeric facts.
                .number("actor.health")
                .number("victim.health")
                .number("damage")
                .number("combo")
                // Boolean flags (the activator's pose/state).
                .flag("sneaking")
                .flag("blocking")
                .flag("flying")
                .build();
    }
}
