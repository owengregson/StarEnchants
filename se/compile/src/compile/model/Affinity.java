package compile.model;

/**
 * Where an effect's work may run; the {@code Sink} routes by this so an effect author never names a
 * scheduler and cannot structurally write a Folia bug (docs/architecture.md §3.6). Ordered by increasing
 * dispatch reach so folding an effect list is a {@link #max(Affinity)} (ordinal) reduction.
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
