package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import schema.spec.D;

/**
 * {@code VELOCITY} — the canonical movement primitive (docs/v3-directives.md §C), which REPLACED the now-deleted
 * {@code THROW}/{@code LAUNCH}/{@code KNOCKBACK} kinds (collapse = delete the redundant heads, authors use this
 * one). {@code mode=add} adds the {@code x/y/z} vector to each target's velocity (what THROW/LAUNCH did);
 * {@code mode=away} shoves each target back from the activator with {@code strength} (what KNOCKBACK did).
 * {@link Affinity#TARGET_ENTITY}: each push routes to the target's own thread.
 */
public final class VelocityEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("VELOCITY")
            .param("mode", D.enumOf("add", "away").def("add"))
            .param("x", D.DOUBLE.def(0))
            .param("y", D.DOUBLE.def(0))
            .param("z", D.DOUBLE.def(0))
            .param("strength", D.DOUBLE.min(0).def(0))
            .target("who", T.VICTIM)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Apply velocity to the target(s): mode=add uses x/y/z; mode=away knocks them back from the "
                    + "activator with strength. Replaces THROW/LAUNCH/KNOCKBACK.")
            .example("VELOCITY:add:0:1.2:0")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        String mode = ctx.str("mode");
        if ("away".equalsIgnoreCase(mode)) {
            // mode=away: knock each target back from the activator (reading actor().getLocation() mirrors the
            // former KNOCKBACK behavior); the dispatcher derives the away-from-actor direction.
            Location from = ctx.actor().getLocation();
            double strength = ctx.dbl("strength");
            for (LivingEntity target : ctx.targets("who")) {
                sink.knockback(target, from, strength);
            }
        } else {
            double x = ctx.dbl("x");
            double y = ctx.dbl("y");
            double z = ctx.dbl("z");
            for (LivingEntity target : ctx.targets("who")) {
                sink.launch(target, x, y, z);
            }
        }
    }
}
