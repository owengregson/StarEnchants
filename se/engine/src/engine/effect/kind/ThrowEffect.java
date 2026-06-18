package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import schema.spec.D;

/**
 * {@code THROW} — add a velocity vector to the target(s) (docs/architecture.md §7). A back-compat alias of
 * {@code VELOCITY} (mode=add): {@link #run} delegates to {@link VelocityEffect#apply}, so the {@code launch}
 * logic lives in exactly one place. {@link Affinity#TARGET_ENTITY}: the {@code Sink} routes each push to the
 * target's own thread (one hop on Folia).
 */
public final class ThrowEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("THROW")
            .param("x", D.DOUBLE)
            .param("y", D.DOUBLE)
            .param("z", D.DOUBLE)
            .target("who", T.VICTIM)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Add a velocity vector to the target.")
            .example("THROW:0:1.2:0")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        VelocityEffect.apply(ctx, sink, "add", ctx.dbl("x"), ctx.dbl("y"), ctx.dbl("z"), 0);
    }
}
