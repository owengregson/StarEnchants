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
 * {@code SOUL_COST_EXEMPT} — for a window, nothing the holder does costs them souls.
 *
 * <p>{@code REMOVE_SOULS} is the only other soul primitive and it only ever SPENDS; the economy had no way to
 * express a waiver at all. This one sits on the paying side of BOTH debit paths: gate 10, which prices a
 * {@code soul-cost} ability, and the {@code REMOVE_SOULS}-on-self effect, which never passes gate 10. An
 * exempt activation also fires OUTSIDE soul mode — "deducts none" cannot mean "still needs an active gem" —
 * and does not step the soul-cost escalation ladder, since nothing was spent to escalate.
 *
 * <p>The refund line rides the window rather than being a separate {@code MESSAGE}: a waiver happens inside
 * somebody else's later activation, where no authored op of this item is running. {@code feedback-threshold}
 * is what keeps a stream of cheap procs from spamming the chat while a real refund is still announced.
 */
public final class SoulCostExemptEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("SOUL_COST_EXEMPT")
            .param("duration", D.TICKS.min(1), "how long the holder's soul costs are waived")
            .param("feedback-threshold", D.INT.min(0).def(10),
                    "a waiver must EXCEED this many souls to send `message`")
            .param("message", D.STRING.def(""),
                    "line sent on each waiver above the threshold ({souls} = the amount waived); empty = silent")
            .target("who", T.SELF)
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Waive every soul cost charged to each target for `duration`. Both debit paths are covered: "
                    + "a soul-cost ability's gate charge and a REMOVE_SOULS aimed at the holder's own gem. "
                    + "While exempt a soul-cost ability fires even with no gem active, and its escalating "
                    + "price stops advancing — a free activation cannot raise the next one. Each waiver above "
                    + "`feedback-threshold` sends `message` with {souls} filled in, so the small change stays "
                    + "quiet. Re-arming replaces the window rather than extending it.")
            .example("{ SOUL_COST_EXEMPT: { duration: 300, feedback-threshold: 10, "
                    + "message: \"&a&lPET (&aTesla&a&l): &a+{souls} souls!\", who: \"@Self\" } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        int duration = ctx.integer("duration");
        int threshold = ctx.integer("feedback-threshold");
        String message = ctx.str("message");
        for (LivingEntity target : ctx.targets("who")) {
            if (target instanceof Player player) {
                sink.armSoulExempt(player, duration, threshold, message);
            }
        }
    }
}
