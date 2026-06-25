package engine.effect;

import engine.sink.Sink;
import engine.spec.EffectSpec;

/**
 * One stateless, self-describing effect kind (docs/architecture.md §3.1, §7). Systems walk compiled
 * abilities and invoke {@link #run} per effect without knowing what any kind does; adding a kind is
 * implementing this interface and registering it in one place.
 *
 * <p>Implementations MUST be stateless — one shared instance is reused across all activations and
 * threads — and MUST emit every result through the {@link Sink}, never touching entities, blocks,
 * worlds, or schedulers directly (§3.5).
 */
public interface EffectKind {

    /** This kind's self-describing signature: params, affinity, target slots (§7). */
    EffectSpec spec();

    /** Execute one activation: read args/targets from {@code ctx}, emit intents to {@code sink}. Hot path — no parsing, no entity touch, no scheduling. */
    void run(EffectCtx ctx, Sink sink);

    /**
     * Deactivation half of the HELD/PASSIVE start/stop lifecycle (docs/v3-directives.md §B, ADR-0022).
     * A maintained-buff kind overrides this to emit the inverse of its {@link #run} (e.g.
     * {@code POTION.stop} removes the potion it applied); one-shot kinds leave it a no-op. Called
     * UNCONDITIONALLY on unequip — never gated by chance/cooldown/world — so a buff can never leak; the
     * engine only stops what it started, so a no-op stop is always safe. Same contract as {@link #run}.
     */
    default void stop(EffectCtx ctx, Sink sink) {
    }

    /** The canonical head this kind registers under, e.g. {@code DAMAGE}. */
    default String head() {
        return spec().head();
    }
}
