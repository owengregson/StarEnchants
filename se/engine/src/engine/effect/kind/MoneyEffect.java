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
 * {@code MODIFY_MONEY} — the canonical economy primitive (docs/v3-directives.md §C), collapsing
 * {@code GIVE_MONEY}/{@code TAKE_MONEY} and adding {@code STEAL_MONEY} via the {@code transfer} mode:
 *
 * <ul>
 *   <li>{@code give} — deposit {@code amount} into each resolved player target;</li>
 *   <li>{@code take} — withdraw {@code amount} from each resolved player target;</li>
 *   <li>{@code transfer} — withdraw from each target AND deposit the total into the ACTIVATOR (steal).</li>
 * </ul>
 *
 * <p>The transfer counterpart is fixed to the activator rather than a second selector, because an effect
 * resolves a single selector — the selector picks the "other" party, the actor is the constant end. This
 * REPLACED the now-deleted {@code GIVE_MONEY}/{@code TAKE_MONEY} kinds (collapse = delete the redundant heads).
 * A no-op without an economy provider. {@link Affinity#TARGET_ENTITY}.
 */
public final class MoneyEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("MODIFY_MONEY")
            .param("amount", D.DOUBLE.min(0))
            .param("mode", D.enumOf("give", "take", "transfer").def("give"))
            .target("who", T.SELF)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Modify a player target's balance: give to them, take from them, or transfer (take from the "
                    + "target and give the total to the activator). Replaces GIVE_MONEY/TAKE_MONEY/STEAL_MONEY.")
            .example("MODIFY_MONEY:100:give:@Self")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        double amount = ctx.dbl("amount");
        boolean transfer = "transfer".equalsIgnoreCase(ctx.str("mode"));
        boolean take = transfer || "take".equalsIgnoreCase(ctx.str("mode"));
        int taken = 0;
        for (LivingEntity target : ctx.targets("who")) {
            if (target instanceof Player p) {
                if (take) {
                    sink.takeMoney(p, amount);
                    taken++;
                } else {
                    sink.giveMoney(p, amount);
                }
            }
        }
        if (transfer && taken > 0 && ctx.actor() != null) {
            sink.giveMoney(ctx.actor(), amount * taken); // the activator gains what was taken (steal)
        }
    }
}
