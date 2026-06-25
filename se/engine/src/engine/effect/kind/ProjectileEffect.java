package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import schema.spec.D;

/**
 * {@code PROJECTILE} — launch one or more projectiles of an entity type from the activator's eye
 * (docs/v3-directives.md §C; covers the Cosmic Enchants-style {@code SPAWN_ARROWS} as {@code PROJECTILE:ARROW:<count>}).
 * Stateless; emits one {@code launchProjectile} intent for the firing player and never touches an entity
 * directly. No target slot: the volley spawns from the actor, not a resolved selector. The {@code type} is a
 * handle arg resolved to an interned id at compile time (§9). {@link Affinity#TARGET_ENTITY}: a
 * shooter-context call, routed to the shooter's own thread.
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
