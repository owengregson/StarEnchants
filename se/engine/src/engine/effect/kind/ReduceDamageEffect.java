package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import schema.spec.D;

/**
 * {@code REDUCE_DAMAGE} — reduce incoming damage by a percentage (docs/architecture.md §7).
 * Stateless; contributes a delta to the additive defense bucket the damage arbiter folds
 * (§6.1) and never reads the event or an entity directly. {@link Affinity#CONTEXT_LOCAL}:
 * it applies on the firing thread.
 */
public final class ReduceDamageEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("REDUCE_DAMAGE")
            .param("percent", D.DOUBLE.min(0).max(100))
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Reduce incoming damage by a percentage (additive defense bucket, §6.1).")
            .example("REDUCE_DAMAGE:15")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        sink.addDamageReduction(ctx.dbl("percent") / 100.0);
    }
}
