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
 * {@code MOVEMENT_SPEED} — set the player target(s)' walk speed for a span of ticks, then restore the
 * vanilla default (docs/v3-directives.md §C). Stateless; emits one {@code movementSpeed} intent per resolved
 * player target and never touches an entity directly. {@code speed} is the Bukkit walk speed (vanilla default
 * {@code 0.2}, max {@code 1.0}). A PASSIVE (while-worn) speed belongs to the HELD/REPEATING lifecycle; this is
 * the triggered, timed form. {@link Affinity#TARGET_ENTITY}: routed to each target's own thread.
 */
public final class MovementSpeedEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("MOVEMENT_SPEED")
            .param("speed", D.DOUBLE.range(-1, 1))
            .param("ticks", D.TICKS.def(200))
            .target("who", T.SELF)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Set the player target's walk speed for a span of ticks, then restore the default (0.2).")
            .example("MOVEMENT_SPEED:0.4:200")
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
