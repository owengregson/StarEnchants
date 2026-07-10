package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import schema.spec.D;

/**
 * {@code PROJECTILE} — launch projectiles of an entity type from the activator's eye (§C). No target slot:
 * the volley spawns from the actor, not a resolved selector. {@code type} interned at compile (§9). ADR-0049:
 * for an explosive projectile (a fireball), {@code yield} sets the blast size and {@code incendiary} lights
 * fires (Hellfire).
 */
public final class ProjectileEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("PROJECTILE")
            .param("type", D.entityType())
            .param("count", D.INT.min(1).def(1))
            .param("speed", D.DOUBLE.min(0).def(1.5))
            .param("yield", D.DOUBLE.def(-1)) // -1 sentinel = vanilla default (no min: a bad default would fail the range check)
            .param("incendiary", D.BOOL.def(false))
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Launch count projectiles of a type from the activator's eye (covers SPAWN_ARROWS via the ARROW "
                    + "type). For an explosive projectile, yield sets the blast (-1 = vanilla default) and "
                    + "incendiary lights fires.")
            .example("{ PROJECTILE: { type: FIREBALL, count: 1, speed: 1.5, yield: 2, incendiary: true } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        sink.launchProjectile(ctx.actor(), ctx.integer("type"), ctx.integer("count"), ctx.dbl("speed"),
                ctx.dbl("yield"), ctx.bool("incendiary"));
    }
}
