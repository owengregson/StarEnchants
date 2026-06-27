package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import java.util.UUID;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import schema.spec.D;

/**
 * {@code REMOVE_SOULS} — debit souls from a soul gem (§D). {@code @Self} (default) charges the activator's
 * active gem ({@link EffectCtx#activeGem()}); {@code @Victim} drains the enemy's own gem (resolved in the soul
 * service). The spend is atomic in the {@code SoulLedger} and the PDC write runs on that player's thread.
 */
public final class RemoveSoulsEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("REMOVE_SOULS")
            .param("amount", D.INT.min(1))
            .target("who", T.SELF)
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Debit souls from a soul gem: @Self (default) charges the activator's active gem, @Victim "
                    + "drains the target's own gem. A no-op when that player is not in soul mode.")
            .example("{ REMOVE_SOULS: { amount: 5 } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        int amount = ctx.integer("amount");
        Player actor = ctx.actor();
        if (amount <= 0) {
            return;
        }
        for (LivingEntity target : ctx.targets("who")) {
            if (!(target instanceof Player player)) {
                continue;
            }
            if (player.equals(actor)) {
                UUID gemId = ctx.activeGem(); // the activator's seeded active gem
                if (gemId != null) {
                    sink.removeSouls(player, gemId, amount);
                }
            } else {
                sink.removeSoulsFrom(player, amount); // the enemy's own gem, resolved in the soul service
            }
        }
    }
}
