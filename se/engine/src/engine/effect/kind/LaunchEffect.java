package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.entity.LivingEntity;
import schema.spec.D;

/**
 * {@code LAUNCH} — add to the target(s) velocity (knockback / fling)
 * (docs/architecture.md §7). Stateless; emits a {@code launch} intent per resolved
 * target and never moves an entity directly. {@link Affinity#TARGET_ENTITY}: the
 * {@code Sink} routes each push to the target's own thread (one hop on Folia).
 */
public final class LaunchEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("LAUNCH")
            .param("x", D.DOUBLE)
            .param("y", D.DOUBLE)
            .param("z", D.DOUBLE)
            .target("who", T.VICTIM)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Add to the target(s) velocity (knockback / fling).")
            .example("LAUNCH:0:1.2:0")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        double x = ctx.dbl("x");
        double y = ctx.dbl("y");
        double z = ctx.dbl("z");
        for (LivingEntity target : ctx.targets("who")) {
            sink.launch(target, x, y, z);
        }
    }
}
