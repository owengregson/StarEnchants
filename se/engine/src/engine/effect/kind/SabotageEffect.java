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

/** {@code SABOTAGE} — arm Rocket Escape's one-second, level-scaled failure roll. */
public final class SabotageEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("SABOTAGE")
            .param("level", D.INT.min(1).max(5))
            .param("duration", D.TICKS.def(20))
            .param("cost", D.INT.min(0).def(8))
            .param("cost-period", D.TICKS.def(20))
            .param("anti-swap", D.TICKS.def(5))
            .target("who", T.VICTIM)
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Require settled held weapon, active positive soul mode, and no Soul Trap; charge eight souls "
                    + "at most once per second and mark the victim for Rocket Escape's 10% per level failure roll.")
            .example("{ SABOTAGE: { level: 5 } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        Player actor = ctx.actor();
        if (actor == null) {
            return;
        }
        for (LivingEntity target : ctx.targets("who")) {
            if (target instanceof Player victim) {
                sink.sabotage(actor, victim, ctx.integer("level"), ctx.integer("duration"),
                        ctx.integer("cost"), ctx.integer("cost-period"), ctx.integer("anti-swap"));
            }
        }
    }
}
