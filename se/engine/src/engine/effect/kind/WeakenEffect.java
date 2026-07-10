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
 * {@code WEAKEN} — debuff the target's outgoing damage by a percent for a duration (ADR-0049 Destruction). The
 * debuff is NON-STACKING (the store keeps the stronger percent, never the sum). Player-only; consulted by the
 * combat dispatcher on the target's later attack side.
 */
public final class WeakenEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("WEAKEN")
            .param("percent", D.DOUBLE.min(0))
            .param("duration", D.TICKS.def(100))
            .target("who", T.VICTIM)
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Debuff the target's outgoing damage by a percent for a duration in ticks (non-stacking). "
                    + "Player targets only; default target the combat victim.")
            .example("{ WEAKEN: { percent: 15, duration: 100, who: \"@Victim\" } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        double percent = ctx.dbl("percent");
        int duration = ctx.integer("duration");
        for (LivingEntity target : ctx.targets("who")) {
            if (target instanceof Player p) {
                sink.weaken(p, percent, duration);
            }
        }
    }
}
