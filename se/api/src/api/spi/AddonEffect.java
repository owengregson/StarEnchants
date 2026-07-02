package api.spi;

/**
 * One stateless, self-describing effect kind contributed by a third-party add-on (docs/architecture.md §7,
 * ADR-0038) — the public counterpart of the engine's internal {@code EffectKind}. Register an instance via
 * {@link api.StarEnchantsApi#registerEffect(AddonEffect)}; its {@link #spec() head} then becomes authorable
 * in content YAML exactly like a built-in effect.
 *
 * <p>Implementations MUST be stateless — one shared instance is reused across all activations and threads —
 * and MUST emit every result through the {@link AddonSink}, never touching entities, blocks, worlds, or
 * schedulers directly. This is the same hot-path contract the engine's built-in kinds hold: {@link #run} is
 * called on the firing thread with no parsing, no entity touch, and no scheduling.
 */
public interface AddonEffect {

    /** This kind's self-describing signature: head, params, affinity, target slots (§7). */
    AddonSpec spec();

    /** Execute one activation: read args/targets from {@code ctx}, emit intents to {@code sink}. Hot path — stateless, no parsing, no entity touch, no scheduling. */
    void run(AddonEffectCtx ctx, AddonSink sink);

    /**
     * Deactivation half of the HELD/PASSIVE start/stop lifecycle (ADR-0022): a maintained-buff kind
     * overrides this to emit the inverse of its {@link #run}; one-shot kinds leave it a no-op. Called
     * UNCONDITIONALLY on unequip, so a buff can never leak. Same stateless contract as {@link #run}.
     */
    default void stop(AddonEffectCtx ctx, AddonSink sink) {
    }
}
