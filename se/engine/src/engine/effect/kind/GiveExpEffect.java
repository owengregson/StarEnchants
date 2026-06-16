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
 * {@code GIVE_EXP} — grant experience points to the player target(s)
 * (docs/architecture.md §7). Stateless; emits a {@code giveExp} intent per resolved
 * target that is a player and never touches an entity directly.
 * {@link Affinity#TARGET_ENTITY}: granting experience mutates the target, so the
 * {@code Sink} routes each intent to the owning player's region thread (§3.6) —
 * declaring it here is all an author does.
 */
public final class GiveExpEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("GIVE_EXP")
            .param("amount", D.INT.min(0))
            .target("who", T.SELF)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Grant experience points to the player target.")
            .example("GIVE_EXP:50")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        int amount = ctx.integer("amount");
        for (LivingEntity target : ctx.targets("who")) {
            if (target instanceof Player p) {
                sink.giveExp(p, amount);
            }
        }
    }
}
