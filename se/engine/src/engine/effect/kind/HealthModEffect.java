package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.entity.LivingEntity;
import schema.spec.D;

/**
 * {@code MODIFY_HEALTH} — the canonical current-health primitive (docs/v3-directives.md §C),
 * collapsing {@code HEAL} (and the HARM / lifesteal / STEAL_HEALTH family) into one parameterized
 * kind. Distinct from {@code HEALTH}, which shifts a target's <em>maximum</em> health attribute.
 *
 * <ul>
 *   <li>{@code give} — restore {@code amount} health to each resolved target (heal);</li>
 *   <li>{@code take} — deal {@code amount} direct health damage to each resolved target;</li>
 *   <li>{@code transfer} — damage each target AND heal the ACTIVATOR by the same total (lifesteal);</li>
 *   <li>{@code set} — set each target's current health TO {@code amount} (the EE {@code REDUCE_HEARTS}
 *       drop-to-N-HP), clamped to [0, max].</li>
 * </ul>
 *
 * <p>The transfer counterpart is fixed to the activator rather than a second selector, because an
 * effect resolves a single selector (mirrors {@link MoneyEffect}). Composed entirely from the existing
 * {@code heal}/{@code damage} intents — no new {@code Sink} method. {@link Affinity#TARGET_ENTITY}: the
 * wider of the two (a mode can mutate the target and/or the activator on a possibly-different region),
 * so the {@code Sink} routes each intent to the owning thread (§3.6).
 */
public final class HealthModEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("MODIFY_HEALTH")
            .param("amount", D.DOUBLE.min(0))
            .param("mode", D.enumOf("give", "take", "transfer", "set").def("give"))
            .target("who", T.SELF)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Modify a target's health: give heals them, take deals direct health damage, transfer "
                    + "(lifesteal) damages the target and heals the activator by the same amount, set forces "
                    + "their health to the amount. Replaces HEAL.")
            .example("MODIFY_HEALTH:4:give:@Self")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        double amount = ctx.dbl("amount");
        String mode = ctx.str("mode");
        if ("set".equalsIgnoreCase(mode)) {
            for (LivingEntity target : ctx.targets("who")) {
                sink.setHealth(target, amount);
            }
            return;
        }
        boolean transfer = "transfer".equalsIgnoreCase(mode);
        boolean take = transfer || "take".equalsIgnoreCase(mode);
        int hit = 0;
        for (LivingEntity target : ctx.targets("who")) {
            if (take) {
                sink.damage(target, amount);
                hit++;
            } else {
                sink.heal(target, amount);
            }
        }
        if (transfer && hit > 0 && ctx.actor() != null) {
            sink.heal(ctx.actor(), amount * hit); // lifesteal: the activator gains what was drained
        }
    }
}
