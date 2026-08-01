package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.entity.LivingEntity;
import schema.spec.D;

/** {@code FILL_OXYGEN} — refill the target(s)' air supply (§7). */
public final class FillOxygenEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("FILL_OXYGEN")
            .param("amount", D.INT.def(-1), "air ticks to add; negative refills completely")
            .target("who", T.SELF)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Add air ticks up to the target's maximum; omit amount for a full refill.")
            .example("{ FILL_OXYGEN: { amount: 20 } }")
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
