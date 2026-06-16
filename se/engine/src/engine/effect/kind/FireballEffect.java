package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import schema.spec.D;

/**
 * {@code FIREBALL} — launch a fireball from the activator (docs/architecture.md §7).
 * Stateless; emits a single {@code fireball} intent for the firing player and never
 * touches an entity directly. No target slot: the projectile spawns from the actor,
 * not from a resolved selector. {@link Affinity#REGION}: spawning a projectile into
 * the world mutates the actor's region, so the {@code Sink} routes the intent to that
 * region's thread (§3.6) — the author only declares it.
 */
public final class FireballEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("FIREBALL")
            .param("yield", D.DOUBLE.min(0).def(1))
            .affinity(Affinity.REGION)
            .doc("Launch a fireball from the activator.")
            .example("FIREBALL:2")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        sink.fireball(ctx.actor(), ctx.dbl("yield"));
    }
}
