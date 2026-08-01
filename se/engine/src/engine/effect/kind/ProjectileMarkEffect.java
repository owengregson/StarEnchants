package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import schema.spec.D;

/** {@code PROJECTILE_MARK} — carry an integer from BOW_FIRE to the projectile's entity impact. */
public final class ProjectileMarkEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("PROJECTILE_MARK")
            .param("value", D.INT.min(1))
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Attach an integer mark to the projectile fired by the current BOW_FIRE activation.")
            .example("{ PROJECTILE_MARK: { value: 3 } }")
            .build();

    @Override public EffectSpec spec() { return SPEC; }

    @Override public void run(EffectCtx ctx, Sink sink) {
        sink.markProjectile(ctx.integer("value"), 1200);
    }
}
