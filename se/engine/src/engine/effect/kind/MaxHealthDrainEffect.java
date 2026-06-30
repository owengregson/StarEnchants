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
 * {@code MAX_HEALTH_DRAIN} — temporarily strip a {@code fraction} of the target's "overhealth" (max health above
 * {@code baseline}) plus a flat {@code amount}, restored after {@code duration}. Cupid's Lovestruck halves a
 * victim's bonus max health for 3s; any "cut their extra hearts" debuff reuses it. The maths + restore are the
 * Sink's (it owns the attribute); this just forwards the parameters per target.
 */
public final class MaxHealthDrainEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("MAX_HEALTH_DRAIN")
            .param("fraction", D.DOUBLE.min(0).max(1).def(0.5))
            .param("baseline", D.DOUBLE.min(0).def(20))
            .param("amount", D.DOUBLE.min(0).def(0))
            .param("duration", D.TICKS.def(60))
            .target("who", T.VICTIM)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Temporarily remove `fraction` of the target's overhealth (max health above `baseline`) plus a "
                    + "flat `amount`, restoring it after `duration` ticks. Default target the combat victim.")
            .example("{ MAX_HEALTH_DRAIN: { fraction: 0.5, baseline: 20, duration: 60, who: \"@Victim\" } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        double fraction = ctx.dbl("fraction");
        double baseline = ctx.dbl("baseline");
        double amount = ctx.dbl("amount");
        int duration = ctx.integer("duration");
        for (LivingEntity target : ctx.targets("who")) {
            sink.drainMaxHealth(target, fraction, baseline, amount, duration);
        }
    }
}
