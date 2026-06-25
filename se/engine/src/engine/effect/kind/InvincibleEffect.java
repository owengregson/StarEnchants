package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.entity.LivingEntity;
import schema.spec.D;

/** {@code INVINCIBLE} — make the target(s) invulnerable for a span of ticks, then restore (§C). */
public final class InvincibleEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("INVINCIBLE")
            .param("ticks", D.TICKS.def(100))
            .target("who", T.SELF)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Make the target invulnerable for a span of ticks, then restore.")
            .example("INVINCIBLE:100")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        int ticks = ctx.integer("ticks");
        for (LivingEntity target : ctx.targets("who")) {
            sink.invincible(target, ticks);
        }
    }
}
