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
 * {@code MODIFY_MONEY} — canonical economy primitive (docs/v3-directives.md §C):
 *
 * <ul>
 *   <li>{@code give} — deposit {@code amount} into each resolved player target;</li>
 *   <li>{@code take} — withdraw {@code amount} from each resolved player target;</li>
 *   <li>{@code transfer} — withdraw {@code amount} from each target AND deposit the total into the ACTIVATOR (steal);</li>
 *   <li>{@code steal_percent} — transfer {@code amount} <em>percent</em> (0..100) of each target's CURRENT
 *       balance to the activator; here {@code amount} is read as a percentage.</li>
 * </ul>
 *
 * <p>Transfer's counterpart is fixed to the activator, not a second selector (an effect resolves one
 * selector). No-op without an economy provider. {@link Affinity#TARGET_ENTITY}.
 */
public final class MoneyEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("MODIFY_MONEY")
            .param("amount", D.DOUBLE.min(0))
            .param("mode", D.enumOf("give", "take", "transfer", "steal_percent").def("give"))
            .target("who", T.SELF)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Modify a player target's balance: give to them, take from them, transfer (take from the target "
                    + "and give the total to the activator), or steal_percent (give the activator that PERCENT of the "
                    + "target's balance — amount is a 0..100 percentage). Replaces GIVE_MONEY/TAKE_MONEY/STEAL_MONEY[_PERCENT].")
            .example("MODIFY_MONEY:100:give:@Self")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        double amount = ctx.dbl("amount");
        String mode = ctx.str("mode");
        if ("steal_percent".equalsIgnoreCase(mode)) {
            // amount is a percentage; the Sink reads each target's live balance and transfers that fraction.
            if (ctx.actor() != null) {
                for (LivingEntity target : ctx.targets("who")) {
                    if (target instanceof Player p) {
                        sink.stealMoneyPercent(p, ctx.actor(), amount / 100.0);
                    }
                }
            }
            return;
        }
        boolean transfer = "transfer".equalsIgnoreCase(mode);
        boolean take = transfer || "take".equalsIgnoreCase(mode);
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
