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
 * {@code FLY} — grant the player target(s) temporary flight for a duration in ticks (§7). {@code speed} rides
 * the same window: an escape burst wants to actually outrun the pursuer, and it is restored with the flight so a
 * lapsed (or logged-out) window can never leave an inflated fly speed behind.
 */
public final class FlyEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("FLY")
            .param("ticks", D.TICKS.def(200))
            .param("speed", D.DOUBLE.range(0, 1).def(0), "fly speed while the window holds; 0 keeps the server's")
            .target("who", T.SELF)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Grant the player temporary flight. speed overrides their fly speed for the window and is "
                    + "restored with it (0, the default, leaves the server's own fly speed alone).")
            .example("{ FLY: { ticks: 200 } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        int ticks = ctx.integer("ticks");
        double speed = ctx.dbl("speed");
        for (LivingEntity target : ctx.targets("who")) {
            if (target instanceof Player p) {
                sink.setFlight(p, ticks, speed);
            }
        }
    }
}
