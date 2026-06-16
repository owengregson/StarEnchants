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
 * {@code TAKE_MONEY} — withdraw money from the player target(s)' account via the economy provider
 * (docs/architecture.md §2, §7), the EE/EA "tax"/"trade" family. Best-effort: the economy withdraws
 * only what the player can afford. A no-op on a server with no economy. Stateless; emits a
 * {@code takeMoney} intent per resolved player target — the {@link Sink} routes it to the global
 * thread. Defaults to {@code @Victim} so an ATTACK enchant taxes the player it hits.
 */
public final class TakeMoneyEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("TAKE_MONEY")
            .param("amount", D.DOUBLE.min(0))
            .target("who", T.VICTIM)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Withdraw money from the player target's account (requires an economy provider).")
            .example("TAKE_MONEY:50:@Victim")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        double amount = ctx.dbl("amount");
        for (LivingEntity target : ctx.targets("who")) {
            if (target instanceof Player p) {
                sink.takeMoney(p, amount);
            }
        }
    }
}
