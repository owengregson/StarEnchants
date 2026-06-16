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
 * {@code THROW} — add a velocity vector to the target(s) (docs/architecture.md §7).
 * Stateless; reuses the {@code launch} intent, emitting one per resolved target and
 * never moving an entity directly. {@link Affinity#TARGET_ENTITY}: the {@code Sink}
 * routes each push to the target's own thread (one hop on Folia).
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
        double x = ctx.dbl("x");
        double y = ctx.dbl("y");
        double z = ctx.dbl("z");
        for (LivingEntity target : ctx.targets("who")) {
            sink.launch(target, x, y, z);
        }
    }
}
