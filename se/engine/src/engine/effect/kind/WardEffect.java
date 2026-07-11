package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import java.util.Locale;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import schema.spec.D;

/**
 * {@code WARD} — arm a typed, timed per-player guard flag with an optional amount (ADR-0053 §7). Writes the
 * {@code WardStore} via the sink; a SEPARATE feature guard reads it back (mob-target calm, /invsee shielding,
 * /near hiding, splash-heal boost). Player-only, defaults to the activator; content re-arms on REPEATING so
 * the flag never outlives the worn mask.
 */
public final class WardEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("WARD")
            .param("type", D.enumOf("mob-target", "invsee", "near", "splash-heal"))
            .param("duration", D.TICKS.def(100))
            .param("amount", D.DOUBLE.def(0))
            .target("who", T.SELF)
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Ward the target player(s) with a typed guard flag for duration ticks: mob-target (mobs don't "
                    + "aggro unless provoked), invsee (others can't open their inventory), near (hidden from the "
                    + "proximity listing), splash-heal (healing splash potions boosted by amount%).")
            .example("{ WARD: { type: splash-heal, duration: 100, amount: 50 } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        int code = typeCode(ctx.str("type"));
        int duration = ctx.integer("duration");
        double amount = ctx.dbl("amount");
        for (LivingEntity target : ctx.targets("who")) {
            if (target instanceof Player player) {
                sink.ward(player, code, duration, amount);
            }
        }
    }

    /** Map the authored enum to the {@code WardStore.Type} ordinal the {@code Sink} expects. */
    private static int typeCode(String type) {
        return switch (type == null ? "" : type.toLowerCase(Locale.ROOT)) {
            case "invsee" -> 1;
            case "near" -> 2;
            case "splash-heal" -> 3;
            default -> 0; // mob-target
        };
    }
}
