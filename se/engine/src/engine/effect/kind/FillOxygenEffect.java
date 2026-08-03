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
 * {@code FILL_OXYGEN} — refill the target(s)' air supply (§7). {@code amount} makes the restore INCREMENTAL —
 * a per-tick trickle that lets the bar visibly fill rather than snapping to full — clamped to the target's own
 * maximum air, so a repeating driver can never bank breath beyond it.
 */
public final class FillOxygenEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("FILL_OXYGEN")
            .param("amount", D.TICKS.def(0), "air ticks to add; 0 refills the bar outright")
            .target("who", T.SELF)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Refill the target's air supply. amount adds that many air ticks instead, clamped to the "
                    + "target's maximum air (0, the default, refills the bar outright).")
            .example("{ FILL_OXYGEN: {} }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        int amount = ctx.integer("amount");
        for (LivingEntity target : ctx.targets("who")) {
            sink.fillAir(target, amount);
        }
    }
}
