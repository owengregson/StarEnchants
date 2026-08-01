package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import schema.spec.D;

/** {@code DISTANCE_DAMAGE} — build one multiplicative damage factor from cumulative near/far distance bands. */
public final class DistanceDamageEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("DISTANCE_DAMAGE")
            .param("distance", D.DOUBLE.min(0))
            .param("near-1", D.DOUBLE.min(0).def(0.25))
            .param("near-2", D.DOUBLE.min(0).def(0.5))
            .param("near-3", D.DOUBLE.min(0).def(0.75))
            .param("near-4", D.DOUBLE.min(0).def(1.0))
            .param("near-5", D.DOUBLE.min(0).def(1.5))
            .param("near-per-level", D.DOUBLE.min(0).def(0.01))
            .param("far-1", D.DOUBLE.min(0).def(2.0))
            .param("far-2", D.DOUBLE.min(0).def(2.5))
            .param("far-3", D.DOUBLE.min(0).def(3.0))
            .param("far-penalty-1", D.DOUBLE.min(0).def(0.01))
            .param("far-penalty-2", D.DOUBLE.min(0).def(0.0125))
            .param("far-penalty-3", D.DOUBLE.min(0).def(0.025))
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Multiply outgoing damage by one factor built from cumulative strict distance thresholds.")
            .example("{ DISTANCE_DAMAGE: { distance: '%distance%' } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        double distance = ctx.dbl("distance");
        double level = ctx.level();
        double factor = 1.0;
        for (int i = 1; i <= 5; i++) {
            if (distance < ctx.dbl("near-" + i)) {
                factor += ctx.dbl("near-per-level") * level;
            }
        }
        for (int i = 1; i <= 3; i++) {
            if (distance > ctx.dbl("far-" + i)) {
                factor -= ctx.dbl("far-penalty-" + i) * level;
            }
        }
        sink.multiplyOutgoingDamage(factor);
    }
}
