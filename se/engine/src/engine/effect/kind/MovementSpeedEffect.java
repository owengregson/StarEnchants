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
 * {@code MOVEMENT_SPEED} — set the player target(s)' walk speed for a span of ticks, then restore the vanilla
 * default (§C). The triggered, timed form; a while-worn speed belongs to HELD/REPEATING.
 */
public final class MovementSpeedEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("MOVEMENT_SPEED")
            .param("speed", D.DOUBLE.range(-1, 1))
            .param("ticks", D.TICKS.def(200))
            .target("who", T.SELF)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Set the player target's walk speed for a span of ticks, then restore the default (0.2).")
            .example("{ MOVEMENT_SPEED: { speed: 0.4, ticks: 200 } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        double speed = ctx.dbl("speed");
        int ticks = ctx.integer("ticks");
        for (LivingEntity target : ctx.targets("who")) {
            if (target instanceof Player p) {
                sink.movementSpeed(p, speed, ticks);
            }
        }
    }
}
