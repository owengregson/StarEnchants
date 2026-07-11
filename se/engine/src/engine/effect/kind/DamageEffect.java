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
 * {@code DAMAGE} — deal extra damage to the target(s) (§7): a flat {@code amount} and/or a
 * {@code percent-of-max} of the TARGET's own maximum health (ADR-0049 Natures Wrath). The two combine (sum) when
 * both are given; at least one should be non-zero.
 */
public final class DamageEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("DAMAGE")
            .param("amount", D.DOUBLE.min(0).def(0))
            .param("percent-of-max", D.DOUBLE.min(0).max(100).def(0))
            .target("who", T.VICTIM)
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Deal extra damage to the target: a flat amount and/or percent-of-max of the target's own "
                    + "maximum health (they sum when both are given).")
            .example("{ DAMAGE: { amount: 6, percent-of-max: 10 } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        double amount = ctx.dbl("amount");
        double percentOfMax = ctx.dbl("percent-of-max");
        for (LivingEntity target : ctx.targets("who")) {
            // The activator attributes any deferred application (ADR-0054) — a WAIT tier (the bleed DoT)
            // or a non-victim target fires an attributed event; a same-hit victim rider folds instead.
            if (amount > 0) {
                sink.damage(target, amount, ctx.actor());
            }
            if (percentOfMax > 0) {
                sink.damagePercentOfMax(target, percentOfMax, ctx.actor());
            }
        }
    }
}
