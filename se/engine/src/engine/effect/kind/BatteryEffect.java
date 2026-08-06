package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import schema.spec.D;

/**
 * {@code BATTERY} — arm the Supernova core (ADR-0071): the wearer's next {@code hits} landed hits taken
 * each bank {@code bank-percent}% of their final damage; the wearer's next landed hit unloads the whole
 * bank as a same-hit rider — even a zero bank (the core is spent regardless, owner-ruled).
 */
public final class BatteryEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("BATTERY")
            // Both read INSIDE the target loop, so each armed body prices itself (ADR-0076). Declared rather
            // than left implicit: PerTargetParamTest counts the reads, so a later hoist cannot pass silently.
            .param("bank-percent", D.DOUBLE.range(0, 100).def(20).perTarget())
            .param("hits", D.INT.range(1, 10).def(3).perTarget())
            .target("who", T.SELF)
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Arm a damage battery on the wearer: the next `hits` landed hits they take each "
                    + "bank `bank-percent`% of the final damage; their next landed hit on an enemy "
                    + "unloads the entire bank as bonus damage on that hit, then the core resets — "
                    + "a hit with nothing banked still spends the core. No time limit; the ability "
                    + "cooldown paces re-arms. Cleared on death.")
            .example("{ BATTERY: { bank-percent: 20, hits: 3 } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        for (LivingEntity who : ctx.targets("who")) {
            if (who instanceof Player p) {
                sink.armBattery(p, ctx.dbl("bank-percent") / 100.0, ctx.integer("hits"));
            }
        }
    }
}
