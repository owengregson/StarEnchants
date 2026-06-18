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
 * {@code MODIFY_FOOD} — the canonical hunger primitive (docs/v3-directives.md §C), collapsing
 * {@code FEED} into a give/take kind:
 *
 * <ul>
 *   <li>{@code give} — restore {@code amount} food points to each resolved player target (clamped to 20);</li>
 *   <li>{@code take} — drain {@code amount} food points from each resolved player target (clamped to 0).</li>
 * </ul>
 *
 * <p>Only players have a hunger bar, so non-player targets are silently skipped. This REPLACED the
 * now-deleted {@code FEED} kind (collapse = delete the redundant head). {@link Affinity#TARGET_ENTITY}:
 * feeding/draining mutates the target, so the {@code Sink} routes each intent to the target's region
 * thread (§3.6).
 */
public final class FoodEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("MODIFY_FOOD")
            .param("amount", D.INT.min(0))
            .param("mode", D.enumOf("give", "take").def("give"))
            .target("who", T.SELF)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Modify a player target's hunger: give food points (clamped to 20) or take them "
                    + "(clamped to 0). Replaces FEED.")
            .example("MODIFY_FOOD:6:give:@Self")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        int amount = ctx.integer("amount");
        boolean take = "take".equalsIgnoreCase(ctx.str("mode"));
        for (LivingEntity target : ctx.targets("who")) {
            if (target instanceof Player p) {
                if (take) {
                    sink.takeFood(p, amount);
                } else {
                    sink.feed(p, amount);
                }
            }
        }
    }
}
