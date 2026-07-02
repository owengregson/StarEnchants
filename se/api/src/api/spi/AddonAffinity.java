package api.spi;

/**
 * Where an add-on effect's work may run — the public mirror of the engine's dispatch {@code Affinity}
 * (docs/architecture.md §3.6). The engine routes each effect to the correct Folia thread by this value, so
 * an add-on never names a scheduler and cannot structurally write a Folia bug. {@code :api} cannot see the
 * engine's own enum (it lives in {@code compile}), so this is the surface add-ons declare against; the
 * bootstrap adapter maps it back one-to-one. Ordered by increasing dispatch reach, matching the engine.
 */
public enum AddonAffinity {

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
    ASYNC
}
