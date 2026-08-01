package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectHalt;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.entity.LivingEntity;
import schema.spec.D;

/** {@code REQUIRE_VAR} — halt unless a timed variable is present or absent as authored. */
public final class RequireVarEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("REQUIRE_VAR")
            .param("name", D.STRING)
            .param("present", D.BOOL.def(true))
            .target("who", T.SELF)
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Continue only when the named per-player variable has the authored presence state.")
            .example("{ REQUIRE_VAR: { name: feedback-throttle, present: false } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        for (LivingEntity target : ctx.targets("who")) {
            if (sink.hasVar(target, ctx.str("name")) != ctx.bool("present")) {
                throw EffectHalt.INSTANCE;
            }
        }
    }
}
