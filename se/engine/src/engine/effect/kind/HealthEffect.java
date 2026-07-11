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
 * {@code HEALTH} — bonus MAXIMUM health (§7); cf. current-health {@code MODIFY_HEALTH}. On PASSIVE/HELD with
 * the default {@code @Self} it is a maintained worn bonus: the {@code MaxHealthDriver} reconciles ONE
 * plugin-owned attribute modifier from live worn state (additive across sources, suppression-aware,
 * crash/relog-proof), and the lifecycle executor skips the effect body — {@link #run} never fires there.
 * Unlike the {@code HEALTH_BOOST} potion pool (Overload's, by design), stacked sources ADD instead of
 * amplifier-clobbering, and nothing can milk it away. On any other trigger/target, {@link #run} is a direct
 * PERMANENT base shift.
 */
public final class HealthEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("HEALTH")
            .param("amount", D.DOUBLE.min(0))
            .target("who", T.SELF)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Bonus maximum health. On PASSIVE/HELD @Self it is a maintained worn bonus (reconciled, additive "
                    + "across sources, removed on unequip); on event triggers it is a permanent base shift.")
            .example("{ HEALTH: { amount: 4 } }")
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
