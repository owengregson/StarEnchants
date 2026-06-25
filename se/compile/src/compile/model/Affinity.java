package compile.model;

/**
 * Where an effect's work is allowed to run — a property declared per effect kind
 * and folded (MAX) to the owning {@link Ability} at compile time
 * (docs/architecture.md §3.6). The engine's {@code Sink} routes intents by this
 * value, so an effect author never names a scheduler and cannot, structurally,
 * write a Folia bug.
 *
 * <p>Constants are ordered by increasing dispatch reach, so folding an effect list to an
 * ability-level affinity is a simple {@link #max(Affinity)} reduction.
 */
public enum Affinity {

    /** Runs inline on the firing region thread — zero scheduler hop on Paper and Folia. */
    CONTEXT_LOCAL,

    /** Routed to the resolved target entity's thread (one hop on Folia). */
    TARGET_ENTITY,

    /** Routed to a block/location's owning region thread. */
    REGION,

    /** Discovered targets in an area, each batched to its own region thread. */
    AOE,

    /** The global / region-agnostic thread. */
    GLOBAL,

    /** Off the main threads entirely (async-safe work only). */
    ASYNC;

    /** The wider-reaching of {@code this} and {@code other}; folds an ability's effects to one affinity. */
    public Affinity max(Affinity other) {
        return this.ordinal() >= other.ordinal() ? this : other;
    }
}
