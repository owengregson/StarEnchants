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
 *
 * <p>{@code cap} bounds ONE reflected hit in absolute health, which a percent alone cannot do: a 100% reflect on a
 * heavy swing would otherwise scale without limit. {@code feedback} is the per-hit line the afflicted sees when
 * the reflect actually lands, so the debuff reads as an event rather than unexplained self-damage; its
 * {@code {damage}} token carries the health actually returned (post-cap).
 */
public final class ReflectEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("REFLECT")
            .param("percent", D.DOUBLE.min(0))
            .param("duration", D.TICKS.def(80))
            .param("cap", D.DOUBLE.min(0).def(0), "flat per-hit ceiling on the health returned; 0 = uncapped")
            .param("feedback", D.STRING.def(""), "per-hit line to the afflicted; {damage} = health returned")
            .target("who", T.VICTIM)
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Mark the target so a percent of their own outgoing damage is reflected back onto them for a "
                    + "duration in ticks (Hex). Player targets only; default target the combat victim. cap is a "
                    + "flat per-hit ceiling on the health returned (0 = uncapped); feedback is an optional chat "
                    + "line sent to the afflicted on each reflected hit, with {damage} filled in.")
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
        double cap = ctx.dbl("cap");
        String feedback = ctx.str("feedback");
        for (LivingEntity target : ctx.targets("who")) {
            if (target instanceof Player p) {
                sink.reflectMark(p, percent, cap, feedback, duration);
            }
        }
    }
}
