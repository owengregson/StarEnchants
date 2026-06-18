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
 * {@code VELOCITY} — the canonical movement primitive (docs/v3-directives.md §C), collapsing
 * {@code THROW}/{@code LAUNCH}/{@code KNOCKBACK}. {@code mode=add} adds the {@code x/y/z} vector to each
 * target's velocity (what THROW/LAUNCH did, byte-identically); {@code mode=away} shoves each target back
 * from the activator with {@code strength} (what KNOCKBACK did). The shared {@link #apply} is the ONE place
 * the four heads converge, so there is no duplicated logic — THROW/LAUNCH/KNOCKBACK remain as back-compat
 * aliases that delegate here. {@link Affinity#TARGET_ENTITY}: each push routes to the target's own thread.
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
        apply(ctx, sink, ctx.str("mode"), ctx.dbl("x"), ctx.dbl("y"), ctx.dbl("z"), ctx.dbl("strength"));
    }

    /**
     * The single velocity-application path shared by {@code VELOCITY} and the {@code THROW}/{@code LAUNCH}/
     * {@code KNOCKBACK} aliases. {@code add} emits one {@code launch} intent per target; {@code away} emits one
     * {@code knockback} (from the actor's location) per target. Reading {@code actor().getLocation()} mirrors
     * the long-standing {@code KNOCKBACK} behavior.
     */
    static void apply(EffectCtx ctx, Sink sink, String mode, double x, double y, double z, double strength) {
        if ("away".equalsIgnoreCase(mode)) {
            Location from = ctx.actor().getLocation();
            for (LivingEntity target : ctx.targets("who")) {
                sink.knockback(target, from, strength);
            }
        } else {
            for (LivingEntity target : ctx.targets("who")) {
                sink.launch(target, x, y, z);
            }
        }
    }
}
