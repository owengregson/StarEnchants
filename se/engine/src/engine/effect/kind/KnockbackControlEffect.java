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
 * {@code KNOCKBACK_CONTROL} — scale or cancel an entity's incoming knockback (§C combat-flags). The server's
 * knockback event (a separate Bukkit event the same tick) reads the per-victim flag this arms; {@code duration}
 * defaults to {@code 2} ticks since that knockback lands the same tick as the hit.
 */
public final class KnockbackControlEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("KNOCKBACK_CONTROL")
            .param("multiplier", D.DOUBLE.min(0).def(0))
            .param("duration", D.TICKS.def(2))
            .target("who", T.SELF)
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Scale the target's incoming knockback for duration ticks: 0 cancels it, 0.5 halves it, "
                    + "2 doubles it (default: cancel for 2 ticks). Use on DEFENSE for your own knockback, "
                    + "or on ATTACK with who: victim for the knockback you deal.")
            .example("{ KNOCKBACK_CONTROL: { multiplier: 0 } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        double multiplier = ctx.dbl("multiplier");
        int duration = ctx.integer("duration");
        for (LivingEntity who : ctx.targets("who")) {
            sink.controlKnockback(who, multiplier, duration);
        }
    }
}
