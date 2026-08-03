package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import engine.stores.OutgoingDebuffStore;
import java.util.Locale;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import schema.spec.D;

/**
 * {@code OUTGOING_DEBUFF} — {@code WEAKEN} with the two axes it cannot express: a damage-cause filter, so a
 * bow debuff leaves the victim's melee alone, and a per-hit {@code feedback} line emitted at the hit the
 * debuff actually prices rather than when it was applied, which is what makes a lost swing legible.
 *
 * <p>Shares WEAKEN's store and non-stacking merge, so the two can never double-count one victim.
 */
public final class OutgoingDebuffEffect implements EffectKind {

    public static final EffectSpec SPEC = EffectSpec.of("OUTGOING_DEBUFF")
            .param("percent", D.DOUBLE.min(0))
            .param("duration", D.TICKS.def(100))
            .param("cause", D.enumOf("all", "melee", "projectile").def("all"),
                    "which of the target's own hits the nerf prices")
            .param("feedback", D.STRING.def(""), "line sent to the debuffed player on every hit it prices")
            .target("who", T.VICTIM)
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Debuff the target's outgoing damage by a percent for a duration in ticks, priced only on "
                    + "their melee hits, their projectile hits, or both (cause). feedback is sent to them on "
                    + "every hit the window actually prices. Non-stacking with itself and with WEAKEN: a "
                    + "re-debuff keeps the stronger window and the later expiry, never the sum. Player "
                    + "targets only.")
            .example("{ OUTGOING_DEBUFF: { percent: 50, duration: 80, cause: projectile, "
                    + "feedback: \"&2** UNFOCUSED **\" } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        double percent = ctx.dbl("percent");
        int duration = ctx.integer("duration");
        int causes = causeMask(ctx.str("cause"));
        String feedback = ctx.str("feedback");
        for (LivingEntity target : ctx.targets("who")) {
            if (target instanceof Player player) {
                sink.outgoingDebuff(player, percent, duration, causes, feedback);
            }
        }
    }

    /** Map the authored enum to the {@link OutgoingDebuffStore} cause bits. */
    private static int causeMask(String cause) {
        return switch (cause == null ? "" : cause.toLowerCase(Locale.ROOT)) {
            case "melee" -> OutgoingDebuffStore.CAUSE_MELEE;
            case "projectile" -> OutgoingDebuffStore.CAUSE_PROJECTILE;
            default -> OutgoingDebuffStore.CAUSE_ALL;
        };
    }
}
