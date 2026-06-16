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
 * {@code GIVE_MONEY} — deposit money into the player target(s)' account via the economy provider
 * (docs/architecture.md §2, §7). A no-op on a server with no economy. Stateless; emits a
 * {@code giveMoney} intent per resolved player target and never touches an account directly — the
 * {@link Sink} routes the deposit to the global thread where economy backends expect to run.
 */
public final class GiveMoneyEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("GIVE_MONEY")
            .param("amount", D.DOUBLE.min(0))
            .target("who", T.SELF)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Deposit money into the player target's account (requires an economy provider).")
            .example("GIVE_MONEY:100:@Self")
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
                sink.giveMoney(p, amount);
            }
        }
    }
}
