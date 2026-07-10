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
 * {@code REFLECT} — mark the target so a portion of THEIR outgoing damage reflects back onto them for a duration
 * (ADR-0049 Hex). Player-only (the reflect window is per-player, keyed by the afflicted). Consulted by the combat
 * dispatcher when the marked player later attacks.
 */
public final class ReflectEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("REFLECT")
            .param("percent", D.DOUBLE.min(0))
            .param("duration", D.TICKS.def(80))
            .target("who", T.VICTIM)
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Mark the target so a percent of their own outgoing damage is reflected back onto them for a "
                    + "duration in ticks (Hex). Player targets only; default target the combat victim.")
            .example("{ REFLECT: { percent: 20, duration: 80, who: \"@Victim\" } }")
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
                sink.reflectMark(p, percent, duration);
            }
        }
    }
}
