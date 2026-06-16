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
 * {@code KNOCKBACK} — shove the target(s) away from the activator
 * (docs/architecture.md §7). Stateless; emits one {@code knockback} intent per
 * resolved target with the actor's location as the origin, and never touches an
 * entity directly — the dispatcher derives the away-from-actor direction.
 * {@link Affinity#TARGET_ENTITY}: the push mutates the target's velocity, so on
 * Folia the {@code Sink} routes each intent to the target's region thread.
 */
public final class KnockbackEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("KNOCKBACK")
            .param("strength", D.DOUBLE.min(0))
            .target("who", T.VICTIM)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Knock the target back, away from the activator.")
            .example("KNOCKBACK:1.5")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        double strength = ctx.dbl("strength");
        for (LivingEntity target : ctx.targets("who")) {
            sink.knockback(target, ctx.actor().getLocation(), strength);
        }
    }
}
