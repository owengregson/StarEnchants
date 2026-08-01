package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.entity.LivingEntity;

/** Clear the shared Bleed stack and restore the target's movement state. */
public final class ClearBleedEffect implements EffectKind {
    static final EffectSpec SPEC = EffectSpec.of("CLEAR_BLEED")
            .target("who", T.SELF)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Clear Cosmic Bleed stacks, restoring player walk speed or removing mob Slowness.")
            .example("{ CLEAR_BLEED: { who: \"@Self\" } }")
            .build();

    @Override public EffectSpec spec() { return SPEC; }

    @Override public void run(EffectCtx ctx, Sink sink) {
        for (LivingEntity target : ctx.targets("who")) {
            sink.clearBleed(target);
        }
    }
}
