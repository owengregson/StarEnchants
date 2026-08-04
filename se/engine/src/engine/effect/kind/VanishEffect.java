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
 * {@code VANISH} — take the target off every player's screen until they give themselves away by swinging.
 * Feign Death's felt core.
 *
 * <p>{@code VIEWER_HIDE} is the near miss and it is a different thing: its window is fixed at arm time and
 * nothing can shorten it, so a hidden player could hammer somebody for the whole duration. This one is bounded
 * by the subject's OWN aggression — {@code break-hits} landed outgoing hits and the window closes. Damage they
 * TAKE never spends one, so being found is not the same as being caught.
 *
 * <p>A re-proc REPLACES the window rather than extending it: the fresh duration and a fresh hit allowance, and
 * never the old window's stale timer ending the new one early.
 */
public final class VanishEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("VANISH")
            .param("duration", D.TICKS.min(1).def(30), "ticks the target stays hidden from every player")
            .param("break-hits", D.INT.min(0).def(1),
                    "landed outgoing hits the window absorbs before it breaks; 0 = only the timer ends it")
            .param("var", D.STRING.def(""), "player variable reading 1 while the window is live; empty = none")
            .target("who", T.SELF)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Hide the target from EVERY online player for `duration` ticks — a packet-level hide, so worn "
                    + "armour vanishes with the body. The window breaks early once `break-hits` of the target's "
                    + "own hits LAND (0 = never); damage they take never spends one, so hiding survives being "
                    + "hit but not hitting back. A player who joins mid-window is re-synced, so a vanish cannot "
                    + "be beaten by relogging. While it is live `var` reads 1, and it drops to 0 the moment it "
                    + "ends by any route (timer, hit, quit). A re-proc REPLACES the window: fresh duration, "
                    + "fresh hit allowance.")
            .example("{ VANISH: { duration: 60, break-hits: 2, var: feign.active, who: \"@Self\" } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        int duration = ctx.integer("duration");
        int breakHits = ctx.integer("break-hits");
        String var = ctx.str("var");
        for (LivingEntity target : ctx.targets("who")) {
            if (target instanceof Player subject) {
                sink.vanish(subject, duration, breakHits, var);
            }
        }
    }
}
