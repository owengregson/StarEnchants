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
 * {@code MODIFY_FOOD} — canonical hunger primitive (§C). {@code give}/{@code take} move the bar now; the
 * window modes instead arm a per-player flag the shared {@code FoodLevelChangeEvent} listener reads back,
 * because a meal's nutrition and hunger drain both land on a LATER event this activation cannot see.
 * Player-only; non-player targets are skipped.
 */
public final class FoodEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("MODIFY_FOOD")
            // amount is optional so the window modes need not author a meaningless 0; give/take with no
            // amount is the no-op it already was.
            .param("amount", D.INT.min(0).def(0))
            .param("mode", D.enumOf("give", "take", "scale-gain", "cancel-drain", "absolute").def("give"))
            // A new param rather than widening amount: amount is an INT and a factor is fractional, and
            // widening it would change the compiled arg type of every MODIFY_FOOD already in a pack.
            .param("factor", D.DOUBLE.min(0).def(1),
                    "scale-gain: what a food-level gain is multiplied by; absolute: what the RESULTING level is")
            .param("duration", D.TICKS.def(100), "scale-gain/cancel-drain/absolute: ticks the armed window lasts")
            .target("who", T.SELF)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Modify a player target's hunger. give/take move the bar now (clamped to 20 / to 0). "
                    + "scale-gain multiplies the next food GAIN by factor for duration ticks; absolute instead "
                    + "multiplies the RESULTING food level (a bigger claim, so it wins if both are armed); "
                    + "cancel-drain cancels hunger LOSS for duration ticks. Author the window modes on "
                    + "REPEATING with duration at least the period for an always-on effect while worn — the "
                    + "engine has no unequip teardown, so the window lapses shortly after re-arming stops. "
                    + "Replaces FEED.")
            .example("{ MODIFY_FOOD: { amount: 6, mode: give, who: \"@Self\" } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        String mode = ctx.str("mode").toLowerCase(Locale.ROOT);
        int amount = ctx.integer("amount");
        int duration = ctx.integer("duration");
        double factor = ctx.dbl("factor");
        for (LivingEntity target : ctx.targets("who")) {
            if (!(target instanceof Player p)) {
                continue;
            }
            switch (mode) {
                case "take" -> sink.takeFood(p, amount);
                case "scale-gain" -> sink.foodWindow(p, 0, duration, factor);
                case "cancel-drain" -> sink.foodWindow(p, 1, duration, 0);
                case "absolute" -> sink.foodWindow(p, 2, duration, factor);
                default -> sink.feed(p, amount);
            }
        }
    }
}
