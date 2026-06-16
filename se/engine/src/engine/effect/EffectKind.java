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

    /** The canonical head this kind registers under, e.g. {@code DAMAGE}. */
    default String head() {
        return spec().head();
    }
}
