package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.entity.LivingEntity;

/** {@code HURT_ANIMATION} — play the vanilla hurt status without issuing damage. */
public final class HurtAnimationEffect implements EffectKind {
    private static final EffectSpec SPEC = EffectSpec.of("HURT_ANIMATION")
            .target("who", T.VICTIM)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Play the target's vanilla red hurt animation without causing another damage event.")
            .example("{ HURT_ANIMATION: { who: '@Victim' } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        for (LivingEntity target : ctx.targets("who")) {
            sink.hurtAnimation(target);
        }
    }
}
