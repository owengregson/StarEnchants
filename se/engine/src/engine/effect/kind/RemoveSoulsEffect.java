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
 * {@code REMOVE_SOULS} — debit souls from a soul gem (docs/v3-directives.md §D). Targets the activator by
 * default ({@code @Self} — charge YOUR active gem, the original §D behaviour), but a victim target
 * ({@code @Victim}) drains the ENEMY's own active gem instead (the EE {@code REMOVE_SOULS:…:TARGET}). The
 * activator path spends against {@link EffectCtx#activeGem()} (the seeded active gem on the activation); a
 * victim path resolves the target's gem inside the {@code Sink}/soul service. A no-op when the chosen player
 * is not in soul mode or the gem cannot afford it; the spend is atomic in the {@code SoulLedger} and written
 * through to the gem's PDC on that player's own thread. {@link Affinity#CONTEXT_LOCAL}.
 */
public final class RemoveSoulsEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("REMOVE_SOULS")
            .param("amount", D.INT.min(1))
            .target("who", T.SELF)
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Debit souls from a soul gem: @Self (default) charges the activator's active gem, @Victim "
                    + "drains the target's own gem. A no-op when that player is not in soul mode.")
            .example("REMOVE_SOULS:5")
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
