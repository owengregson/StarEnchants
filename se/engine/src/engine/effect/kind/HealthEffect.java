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
 * {@code HEALTH} — add to the target(s)' MAXIMUM health (docs/architecture.md §7); the bonus is tracked
 * by the dispatcher and restored on unequip. {@link Affinity#TARGET_ENTITY}.
 */
public final class HealthEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("HEALTH")
            .param("amount", D.DOUBLE.min(0))
            .target("who", T.SELF)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Add to the target's maximum health (restored on unequip).")
            .example("HEALTH:4")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        double amount = ctx.dbl("amount");
        for (LivingEntity target : ctx.targets("who")) {
            sink.addMaxHealth(target, amount);
        }
    }
}
