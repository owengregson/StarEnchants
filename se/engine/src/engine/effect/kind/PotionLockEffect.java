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
 * {@code POTION_LOCK} — strip a potion effect from the target(s) and continuously deny it for {@code ticks}
 * (druid's 5s Speed lock on impact, fantasy's Speed lock while webbed). Unlike {@code REMOVE_POTION}, a
 * re-application during the window is removed again — the Sink re-strips every tick until it elapses.
 * {@code effect} is interned at compile (§9).
 */
public final class PotionLockEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("POTION_LOCK")
            .param("effect", D.potionEffect())
            .param("ticks", D.TICKS.def(100))
            .target("who", T.SELF)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Strip a potion effect from the target(s) and continuously deny it for `ticks` (a re-strip each "
                    + "tick), so it cannot be re-applied during the window. Default target self.")
            .example("{ POTION_LOCK: { effect: SPEED, ticks: 100, who: \"@Victim\" } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        int effect = ctx.integer("effect");
        int ticks = ctx.integer("ticks");
        for (LivingEntity target : ctx.targets("who")) {
            sink.potionLock(target, effect, ticks);
        }
    }
}
