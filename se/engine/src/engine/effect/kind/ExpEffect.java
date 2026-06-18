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
 * {@code MODIFY_EXP} — the canonical experience primitive (docs/v3-directives.md §C), collapsing
 * {@code GIVE_EXP} and adding take/steal via the {@code transfer} mode:
 *
 * <ul>
 *   <li>{@code give} — grant {@code amount} experience to each resolved player target;</li>
 *   <li>{@code take} — withdraw {@code amount} experience from each resolved player target;</li>
 *   <li>{@code transfer} — withdraw from each target AND grant the total to the ACTIVATOR (steal).</li>
 * </ul>
 *
 * <p>The transfer counterpart is fixed to the activator rather than a second selector, because an
 * effect resolves a single selector — the selector picks the "other" party, the actor is the constant
 * end (mirrors {@link MoneyEffect}). This REPLACED the now-deleted {@code GIVE_EXP} kind (collapse =
 * delete the redundant head). {@link Affinity#TARGET_ENTITY}: granting/removing XP mutates the target,
 * so the {@code Sink} routes each intent to the owning player's region thread (§3.6).
 */
public final class ExpEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("MODIFY_EXP")
            .param("amount", D.INT.min(0))
            .param("mode", D.enumOf("give", "take", "transfer").def("give"))
            .target("who", T.SELF)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Modify a player target's experience: give to them, take from them, or transfer (take "
                    + "from the target and grant the total to the activator). Replaces GIVE_EXP.")
            .example("MODIFY_EXP:50:give:@Self")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        int amount = ctx.integer("amount");
        boolean transfer = "transfer".equalsIgnoreCase(ctx.str("mode"));
        boolean take = transfer || "take".equalsIgnoreCase(ctx.str("mode"));
        int taken = 0;
        for (LivingEntity target : ctx.targets("who")) {
            if (target instanceof Player p) {
                if (take) {
                    sink.takeExp(p, amount);
                    taken++;
                } else {
                    sink.giveExp(p, amount);
                }
            }
        }
        if (transfer && taken > 0 && ctx.actor() != null) {
            sink.giveExp(ctx.actor(), amount * taken); // the activator gains what was taken (steal)
        }
    }
}
