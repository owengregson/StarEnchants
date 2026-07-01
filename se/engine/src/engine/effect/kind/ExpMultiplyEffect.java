package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import schema.spec.D;

/**
 * {@code EXP_MULTIPLY} — accumulate a factor the EXP_GAIN dispatcher applies to the triggering
 * {@code PlayerExpChangeEvent}'s amount. Scaling the event in place is recursion-safe (granting new XP would
 * re-fire the event); the sink read-back is inline like {@link CancelEffect}.
 */
public final class ExpMultiplyEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("EXP_MULTIPLY")
            .param("factor", D.DOUBLE.min(0).def(2.0))
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Multiply the XP gained (EXP_GAIN trigger) by a factor.")
            .example("{ EXP_MULTIPLY: { factor: 2 } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        sink.multiplyExp(ctx.dbl("factor"));
    }
}
