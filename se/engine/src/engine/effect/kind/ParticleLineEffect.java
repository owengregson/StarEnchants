package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import schema.spec.D;

/**
 * {@code PARTICLE_LINE} — draw a straight line of coloured dust from each {@code who} target's hip to the
 * ACTOR's hip ({@code density} motes per block). KOTH's tether to each nearby player; any "beam to N targets"
 * reuses it (a single moving beam is {@code TETHER}). Geometry here; the coloured per-point draw is the Sink.
 */
public final class ParticleLineEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("PARTICLE_LINE")
            .param("particle", D.particle())
            .param("r", D.INT.range(0, 255).def(255))
            .param("g", D.INT.range(0, 255).def(255))
            .param("b", D.INT.range(0, 255).def(255))
            .param("size", D.DOUBLE.min(0).def(1))
            .param("density", D.DOUBLE.min(0).def(2))
            .param("height", D.DOUBLE.def(1))
            .target("who", T.AOE)
            .affinity(Affinity.REGION)
            .doc("Draw a coloured-dust line from each 'who' target's hip to the actor's hip, `density` motes per "
                    + "block, tinted r/g/b (0-255). Pair with who: @AllPlayers{r=N} for a fan of tethers.")
            .example("{ PARTICLE_LINE: { particle: REDSTONE, r: 255, g: 255, b: 255, density: 2, who: \"@AllPlayers{r=7}\" } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        Player actor = ctx.actor();
        if (actor == null) {
            return;
        }
        Location aLoc = actor.getLocation();
        World world = aLoc.getWorld();
        if (world == null) {
            return;
        }
        int particle = ctx.integer("particle");
        int r = ctx.integer("r");
        int g = ctx.integer("g");
        int b = ctx.integer("b");
        float size = (float) ctx.dbl("size");
        double density = ctx.dbl("density");
        double height = ctx.dbl("height");
        double ax = aLoc.getX();
        double ay = aLoc.getY() + height;
        double az = aLoc.getZ();
        for (LivingEntity who : ctx.targets("who")) {
            Location wLoc = who.getLocation();
            if (wLoc.getWorld() != world) {
                continue; // never draw across worlds (also dodges a cross-region location read)
            }
            double sx = wLoc.getX();
            double sy = wLoc.getY() + height;
            double sz = wLoc.getZ();
            double dx = ax - sx;
            double dy = ay - sy;
            double dz = az - sz;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            int steps = Math.max(1, (int) Math.round(dist * density));
            for (int s = 0; s <= steps; s++) {
                double t = (double) s / steps;
                Location point = new Location(world, sx + dx * t, sy + dy * t, sz + dz * t);
                sink.dust(point, particle, r, g, b, size, 1);
            }
        }
    }
}
