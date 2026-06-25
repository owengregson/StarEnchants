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

/** {@code VELOCITY} — canonical movement primitive (§C): {@code add} an x/y/z vector, or shove {@code away} from the activator. */
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
