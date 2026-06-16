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
 * {@code DAMAGE} — deal extra damage to the target(s) (docs/architecture.md §7).
 * Stateless; emits a {@code damage} intent per resolved target and never touches an
 * entity directly. {@link Affinity#CONTEXT_LOCAL}: it applies on the firing thread.
 */
public final class DamageEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("DAMAGE")
            .param("amount", D.DOUBLE.min(0))
            .target("who", T.VICTIM)
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Deal a flat amount of extra damage to the target.")
            .example("DAMAGE:6")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        double amount = ctx.dbl("amount");
        for (LivingEntity target : ctx.targets("who")) {
            sink.damage(target, amount);
        }
    }
}
