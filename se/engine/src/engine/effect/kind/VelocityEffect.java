package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import platform.caps.Regions;
import schema.spec.D;

/** {@code VELOCITY} — canonical movement primitive (§C): {@code add} an x/y/z vector, or shove {@code away} from the activator. */
public final class VelocityEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("VELOCITY")
            .param("mode", D.enumOf("add", "set", "away").def("add"))
            .param("x", D.DOUBLE.def(0))
            .param("y", D.DOUBLE.def(0))
            .param("z", D.DOUBLE.def(0))
            .param("strength", D.DOUBLE.min(0).def(0))
            .param("from", D.enumOf("actor", "victim").def("actor"))
            .target("who", T.VICTIM)
            .affinity(Affinity.TARGET_ENTITY)
            .actorOrigin()
            .doc("Apply velocity to the target(s): mode=add uses x/y/z; mode=away knocks them back from the "
                    + "activator with strength. Replaces THROW/LAUNCH/KNOCKBACK.")
            .example("{ VELOCITY: { mode: add, x: 0, y: 1.2, z: 0 } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        String mode = ctx.str("mode");
        if ("away".equalsIgnoreCase(mode)) {
            Location from;
            if ("victim".equalsIgnoreCase(ctx.str("from"))) {
                LivingEntity victim = ctx.victim();
                if (victim == null) {
                    return;
                }
                try {
                    from = victim.getLocation();
                } catch (RuntimeException unreadable) {
                    Regions.swallowed("VelocityEffect.fromVictim", unreadable);
                    return;
                }
            } else {
                from = ctx.actorOrigin();
            }
            if (from == null) {
                return;
            }
            double strength = ctx.dbl("strength");
            for (LivingEntity target : ctx.targets("who")) {
                sink.knockback(target, from, strength);
            }
        } else {
            double x = ctx.dbl("x");
            double y = ctx.dbl("y");
            double z = ctx.dbl("z");
            boolean set = "set".equalsIgnoreCase(mode);
            for (LivingEntity target : ctx.targets("who")) {
                if (set) {
                    sink.setVelocity(target, x, y, z);
                } else {
                    sink.launch(target, x, y, z);
                }
            }
        }
    }
}
