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
import schema.spec.D;

/**
 * {@code PARTICLE_RING} — a horizontal ring of {@code count} evenly-spaced coloured-dust motes of radius
 * {@code radius}, drawn {@code height} above the target's feet (default @Self), tinted by r/g/b. KOTH's
 * Victorious crown aura; any radius / boss-aura indicator reuses it as pure YAML. The geometry is computed
 * here (firing thread, snapshot-safe value maths); the per-point colour draw is the Sink's {@code dust}.
 */
public final class ParticleRingEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("PARTICLE_RING")
            .param("particle", D.particle())
            .param("r", D.INT.range(0, 255).def(255))
            .param("g", D.INT.range(0, 255).def(255))
            .param("b", D.INT.range(0, 255).def(255))
            .param("size", D.DOUBLE.min(0).def(1))
            .param("radius", D.DOUBLE.min(0).def(3))
            .param("count", D.INT.min(1).def(36))
            .param("height", D.DOUBLE.def(1))
            .target("who", T.SELF)
            .affinity(Affinity.REGION)
            .doc("Draw a horizontal ring of `count` coloured-dust motes of radius `radius` at `height` above the "
                    + "target's feet (default @Self), tinted r/g/b (0-255). A radius / aura indicator.")
            .example("{ PARTICLE_RING: { particle: REDSTONE, r: 255, g: 255, b: 255, radius: 7, count: 60 } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        int particle = ctx.integer("particle");
        int r = ctx.integer("r");
        int g = ctx.integer("g");
        int b = ctx.integer("b");
        float size = (float) ctx.dbl("size");
        double radius = ctx.dbl("radius");
        int count = ctx.integer("count");
        double height = ctx.dbl("height");
        for (LivingEntity who : ctx.targets("who")) {
            Location base = who.getLocation();
            World world = base.getWorld();
            if (world == null) {
                continue;
            }
            double cx = base.getX();
            double cy = base.getY() + height;
            double cz = base.getZ();
            for (int i = 0; i < count; i++) {
                double angle = (2.0 * Math.PI * i) / count;
                Location point = new Location(world, cx + radius * Math.cos(angle), cy, cz + radius * Math.sin(angle));
                sink.dust(point, particle, r, g, b, size, 1);
            }
        }
    }
}
