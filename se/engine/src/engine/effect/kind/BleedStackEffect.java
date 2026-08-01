package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.entity.LivingEntity;
import schema.spec.D;

/** Add one shared Cosmic Bleed stack and apply its stack-derived movement, visual, and Blood Lust effects. */
public final class BleedStackEffect implements EffectKind {
    static final EffectSpec SPEC = EffectSpec.of("BLEED_STACK")
            .param("speed-step", D.DOUBLE.min(0))
            .param("half-floor", D.INT.min(0).def(0))
            .param("blood-lust-floor", D.DOUBLE.min(0))
            .param("blood-lust-scale", D.DOUBLE.min(0))
            .param("primary-block", D.material())
            .param("secondary-block", D.material().optional())
            .param("slow", D.potionEffect())
            .param("blood-lust-particle", D.particle())
            .param("blood-lust-sound", D.sound())
            .target("who", T.VICTIM)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Increment the shared 20-stack Bleed state and apply stack movement, block cues, and nearby allied Blood Lust healing.")
            .example("{ BLEED_STACK: { speed-step: 0.005, blood-lust-floor: 2, blood-lust-scale: 0.05, primary-block: REDSTONE_BLOCK, slow: SLOW, blood-lust-sound: EAT } }")
            .build();

    @Override public EffectSpec spec() { return SPEC; }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        int secondary = ctx.args().has("secondary-block") ? ctx.integer("secondary-block") : -1;
        for (LivingEntity victim : ctx.targets("who")) {
            sink.bleedStack(ctx.actor(), victim, ctx.dbl("speed-step"), ctx.integer("half-floor"),
                    ctx.dbl("blood-lust-floor"), ctx.dbl("blood-lust-scale"), ctx.integer("primary-block"),
                    secondary, ctx.integer("slow"), ctx.integer("blood-lust-particle"),
                    ctx.integer("blood-lust-sound"));
        }
    }
}
