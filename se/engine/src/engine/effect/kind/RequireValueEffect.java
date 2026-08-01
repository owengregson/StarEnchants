package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectHalt;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import schema.spec.D;

/** {@code REQUIRE_VALUE} — halt the remaining effects cleanly unless a numeric expression is in range. */
public final class RequireValueEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("REQUIRE_VALUE")
            .param("value", D.DOUBLE)
            .param("min", D.DOUBLE.def(-Double.MAX_VALUE))
            .param("max", D.DOUBLE.def(Double.MAX_VALUE))
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Continue the current ability only when value is between min and max, inclusive.")
            .example("{ REQUIRE_VALUE: { value: '%victim.isplayer%', min: 1 } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        double value = ctx.dbl("value");
        if (value < ctx.dbl("min") || value > ctx.dbl("max")) {
            throw EffectHalt.INSTANCE;
        }
    }
}
