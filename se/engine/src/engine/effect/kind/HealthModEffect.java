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
 * {@code MODIFY_HEALTH} — canonical current-health primitive (§C); distinct from {@code HEALTH}, which shifts
 * the <em>maximum</em>-health attribute. Transfer's counterpart is fixed to the activator, not a second
 * selector (an effect resolves one selector; mirrors {@link MoneyEffect}).
 */
public final class HealthModEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("MODIFY_HEALTH")
            .param("amount", D.DOUBLE.min(0))
            .param("mode", D.enumOf("give", "take", "transfer", "set").def("give"))
            .target("who", T.SELF)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Modify a target's health: give heals them, take deals direct health damage, transfer "
                    + "(lifesteal) damages the target and heals the activator by the same amount, set forces "
                    + "their health to the amount. Replaces HEAL.")
            .example("{ MODIFY_HEALTH: { amount: 4, mode: give, who: \"@Self\" } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        double amount = ctx.dbl("amount");
        String mode = ctx.str("mode");
        if ("set".equalsIgnoreCase(mode)) {
            for (LivingEntity target : ctx.targets("who")) {
                sink.setHealth(target, amount);
            }
            return;
        }
        boolean transfer = "transfer".equalsIgnoreCase(mode);
        boolean take = transfer || "take".equalsIgnoreCase(mode);
        int hit = 0;
        for (LivingEntity target : ctx.targets("who")) {
            if (take) {
                sink.damage(target, amount);
                hit++;
            } else {
                sink.heal(target, amount);
            }
        }
        if (transfer && hit > 0 && ctx.actor() != null) {
            sink.heal(ctx.actor(), amount * hit); // lifesteal: the activator gains what was drained
        }
    }
}
