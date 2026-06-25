package engine.effect;

import engine.sink.Sink;
import engine.spec.EffectSpec;

/**
 * One effect kind — a stateless, self-describing unit of behavior
 * (docs/architecture.md §3.1, §7). The engine's systems walk compiled abilities and
 * invoke {@link #run} for each effect; a system never knows what any particular kind
 * does. Adding a kind is implementing this one interface and registering it in one
 * place — no switch to edit, no system to grow.
 *
 * <p>Implementations MUST be stateless (a single shared instance is reused across
 * all activations and threads) and MUST emit every result through the {@link Sink};
 * they must not touch entities, blocks, worlds, or schedulers directly (§3.5).
 */
public interface EffectKind {

    /** This kind's self-describing signature: params, affinity, target slots (§7). */
    EffectSpec spec();

    /**
     * Execute one activation of this effect. Hot path: read typed args and targets
     * from {@code ctx}, emit intents to {@code sink} — no parsing, no entity touch,
     * no scheduling.
     */
    void run(EffectCtx ctx, Sink sink);

    /**
     * Tear down this effect for a HELD/PASSIVE source that just became inactive — the deactivation half
     * of the Cosmic Enchants-style start/stop lifecycle (docs/v3-directives.md §B, ADR-0022). The default is a no-op, because
     * most kinds are one-shot and leave no maintained state to undo (a {@code MESSAGE} on equip simply
     * does not un-send on unequip). A <em>maintained-buff</em> kind whose {@link #run} applies a persistent
     * state overrides this to emit the inverse intent — e.g. {@code POTION.stop} removes the potion it
     * applied. Called UNCONDITIONALLY when a started source unequips (never gated by chance/cooldown/world),
     * so a buff can never leak; the engine only stops what it actually started, so a no-op {@code stop} for
     * a one-shot effect is always safe. Same contract as {@link #run}: stateless, intents only, no entity
     * touch, no scheduling.
     */
    default void stop(EffectCtx ctx, Sink sink) {
        // one-shot by default: nothing maintained to undo
    }

    /** The canonical head this kind registers under, e.g. {@code DAMAGE}. */
    default String head() {
        return spec().head();
    }
}
