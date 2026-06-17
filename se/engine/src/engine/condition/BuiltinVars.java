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
 *
 * <p>This factory is the single source of truth for the slot assignment: the compiler
 * lowers conditions against {@link #vocabulary()}{@code .asResolver()} and the runtime
 * {@code engine.run.FactPopulator} reads the same {@link #vocabulary()}, so a compiled
 * condition's slot and the populated buffer agree by construction. The populator sources
 * {@code actor.health}, {@code victim.health}, and the three flags today; {@code damage}
 * and {@code combo} are declared but not yet sourced at runtime (they read 0 until a
 * later increment wires their context) — no shipped content depends on them.
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
