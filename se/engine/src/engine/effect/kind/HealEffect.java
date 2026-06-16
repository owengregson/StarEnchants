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
 * {@code HEAL} — restore health to the target(s) (docs/architecture.md §7).
 * Stateless; emits a {@code heal} intent per resolved target.
 * {@link Affinity#TARGET_ENTITY}: healing mutates the target, so on Folia it routes
 * to the target's region thread (the honest one-hop defense case, §3.6) — declaring
 * it here is all an author does; the Sink does the routing.
 */
public final class HealEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("HEAL")
            .param("amount", D.DOUBLE.min(0))
            .target("who", T.SELF)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Restore a flat amount of health to the target.")
            .example("HEAL:4")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        double amount = ctx.dbl("amount");
        for (LivingEntity target : ctx.targets("who")) {
            sink.heal(target, amount);
        }
    }
}
