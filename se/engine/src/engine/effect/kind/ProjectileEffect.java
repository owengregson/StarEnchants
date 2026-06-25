package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import schema.spec.D;

/**
 * {@code PROJECTILE} — launch projectiles of an entity type from the activator's eye (§C). No target slot:
 * the volley spawns from the actor, not a resolved selector. {@code type} interned at compile (§9).
 */
public final class ProjectileEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("PROJECTILE")
            .param("type", D.entityType())
            .param("count", D.INT.min(1).def(1))
            .param("speed", D.DOUBLE.min(0).def(1.5))
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Launch count projectiles of a type from the activator's eye (covers SPAWN_ARROWS as PROJECTILE:ARROW).")
            .example("PROJECTILE:ARROW:3:1.5")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        sink.launchProjectile(ctx.actor(), ctx.integer("type"), ctx.integer("count"), ctx.dbl("speed"));
    }
}
