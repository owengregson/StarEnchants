package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import schema.spec.D;

/**
 * {@code EXP_MULTIPLY} — accumulate a factor the XP dispatchers apply to the triggering event. Scaling the
 * event in place is recursion-safe (granting new XP would re-fire it); the sink read-back is inline like
 * {@link CancelEffect}.
 *
 * <p>TWO triggers read it, and they round differently on purpose. EXP_GAIN scales an amount already granted,
 * where the nearest whole XP is the honest reading, so it rounds; MINE scales the broken block's own yield,
 * which is a whole-orb quantity, so it truncates (a 7-XP block at x1.25..x2.25 gives 8/10/12/14/15).
 */
public final class ExpMultiplyEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("EXP_MULTIPLY")
            .param("factor", D.DOUBLE.min(0).def(2.0))
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Multiply the XP gained by a factor, on EXP_GAIN and on MINE. EXP_GAIN scales the amount "
                    + "already granted and ROUNDS to the nearest whole XP; MINE scales the broken block's own "
                    + "yield and TRUNCATES, because a block yields whole orbs.")
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
