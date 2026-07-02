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
import platform.caps.Regions;
import schema.spec.D;

/**
 * {@code PARTICLE_LINE} — draw a straight line of coloured dust from each {@code who} target's hip to the
 * ACTOR's hip ({@code density} motes per block). KOTH's tether to each nearby player; any "beam to N targets"
 * reuses it (a single moving beam is {@code TETHER}). The actor anchor is the ADR-0043 origin snapshot; other
 * resolved targets are read live behind the Regions guard; the coloured per-point draw is the Sink.
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
            .actorOrigin()
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
        Location anchor = ctx.actorOrigin();
        if (anchor == null) {
            return; // no actor anchor (uncaptured origin) → no tether
        }
        World world = anchor.getWorld();
        Player actor = ctx.actor();
        int particle = ctx.integer("particle");
        int r = ctx.integer("r");
        int g = ctx.integer("g");
        int b = ctx.integer("b");
        float size = (float) ctx.dbl("size");
        double density = ctx.dbl("density");
        double height = ctx.dbl("height");
        double ax = anchor.getX();
        double ay = anchor.getY() + height;
        double az = anchor.getZ();
        for (LivingEntity who : ctx.targets("who")) {
            Location wLoc;
            if (who == actor) {
                wLoc = anchor; // the actor's own snapshot, never a live remote read
            } else {
                try {
                    wLoc = who.getLocation(); // selector-resolved; may be remote (@AllPlayers across a boundary)
                } catch (RuntimeException unreadable) {
                    Regions.swallowed("ParticleLineEffect.target", unreadable);
                    continue;
                }
            }
            if (wLoc == null || wLoc.getWorld() != world) {
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
