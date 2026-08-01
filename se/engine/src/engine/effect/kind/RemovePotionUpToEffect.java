package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.entity.LivingEntity;
import schema.spec.D;

/** {@code REMOVE_POTION_UP_TO} — clear one potion only through an authored strength tier. */
public final class RemovePotionUpToEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("REMOVE_POTION_UP_TO")
            .param("effect", D.potionEffect())
            .param("max-level", D.INT.min(1))
            .target("who", T.SELF)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Remove the selected potion only when its current 1-based strength is at or below max-level.")
            .example("{ REMOVE_POTION_UP_TO: { effect: BLINDNESS, max-level: 2, who: \"@Self\" } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        int effect = ctx.integer("effect");
        int maxAmplifier = ctx.integer("max-level") - 1;
        for (LivingEntity target : ctx.targets("who")) {
            sink.removePotionUpTo(target, effect, maxAmplifier);
        }
    }
}
