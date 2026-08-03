package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import java.util.List;
import org.bukkit.entity.LivingEntity;
import schema.spec.D;

/**
 * {@code PERIODIC_DAMAGE} — an actor-attributed burn: {@code amount} raw half-hearts every {@code period}
 * ticks for {@code duration}, with an optional {@code feedback} line each pulse. {@code replace} names the
 * vanilla potion DoTs the burn CONVERTS: they are stripped and denied for the window, so a wither-conversion
 * reads as one escalating burn rather than two clocks ticking at once.
 *
 * <p>FREEZE's {@code dot} is the only other periodic hurt and it is frost-themed by construction (a root plus
 * the powder-snow visual); nothing there can carry a fire or wither identity, and its window is a root's
 * window, not a burn's.
 */
public final class PeriodicDamageEffect implements EffectKind {

    public static final EffectSpec SPEC = EffectSpec.of("PERIODIC_DAMAGE")
            .param("amount", D.DOUBLE.min(0), "raw pre-armor half-hearts per pulse (never attack-scaled)")
            .param("period", D.TICKS.def(20))
            .param("duration", D.TICKS.def(100))
            .param("replace", D.potionEffects().def(""),
                    "vanilla potion DoTs this burn converts: stripped and denied for the window")
            .param("feedback", D.STRING.def(""), "line sent to a player target on every pulse")
            .target("who", T.VICTIM)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Burn the target for amount raw half-hearts every period ticks over duration ticks, "
                    + "attributed to the activator (kill credit, era-combat delivery). replace is a "
                    + "comma-separated set of potion effects the burn converts — each is stripped and held "
                    + "off the target for the whole window, so the converted DoT stops ticking on its own. "
                    + "feedback is sent to a player target on every pulse. Two burns on one victim both run: "
                    + "unlike FREEZE, this is not a refreshed window.")
            .example("{ PERIODIC_DAMAGE: { amount: 6, period: 20, duration: 120, replace: WITHER } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        double amount = ctx.dbl("amount");
        int period = ctx.integer("period");
        int duration = ctx.integer("duration");
        List<Integer> replaced = ctx.ids("replace");
        String feedback = ctx.str("feedback");
        for (LivingEntity target : ctx.targets("who")) {
            // The activator attributes every pulse (ADR-0054), exactly as FREEZE's DoT does.
            sink.periodicDamage(target, amount, period, duration, replaced, feedback, ctx.actor());
        }
    }
}
