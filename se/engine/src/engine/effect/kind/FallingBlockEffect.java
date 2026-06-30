package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import schema.spec.D;

/**
 * {@code FALLING_BLOCK} — rain a (2r+1)² grid of falling blocks {@code height} blocks above each target. The
 * blocks never drop an item or hurt, and are removed after {@code ttl} if they never land. When a block LANDS,
 * the {@link engine.sink.FallingBlockCasts}/landing listener fires the actor's {@code IMPACT}-triggered abilities
 * on whatever it landed on — so the impact is fully abstractable (any effects hang off {@code trigger: IMPACT}).
 * {@code carry} is forwarded to the impact as {@code %damage%} (set {@code carry: "%damage%"} to carry this hit).
 * Druid's Terrablender rains grass that deals 1.5× the hit + strips Speed on impact.
 */
public final class FallingBlockEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("FALLING_BLOCK")
            .param("material", D.material())
            .param("radius", D.INT.range(0, 4).def(1))
            .param("height", D.INT.range(0, 12).def(4))
            .param("ttl", D.TICKS.def(40))
            .param("carry", D.DOUBLE.def(0))
            .target("who", T.VICTIM)
            .affinity(Affinity.REGION)
            .doc("Spawn a (2*radius+1)² grid of falling blocks `height` blocks above each target (removed after "
                    + "`ttl` if they never land). A landing block fires the actor's IMPACT abilities on what it "
                    + "hit; `carry` is forwarded to that impact as %damage% (set carry: \"%damage%\").")
            .example("{ FALLING_BLOCK: { material: GRASS_BLOCK, radius: 1, height: 4, carry: \"%damage%\", who: \"@Victim\" } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        int material = ctx.integer("material");
        int radius = ctx.integer("radius");
        int height = ctx.integer("height");
        int ttl = ctx.integer("ttl");
        double carry = ctx.dbl("carry");
        UUID owner = ctx.actor() == null ? null : ctx.actor().getUniqueId();
        for (LivingEntity who : ctx.targets("who")) {
            Location base = who.getLocation();
            World world = base.getWorld();
            if (world == null) {
                continue;
            }
            int bx = base.getBlockX();
            int by = base.getBlockY() + height;
            int bz = base.getBlockZ();
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    // +0.5 centres each block on its column so the grid falls straight down onto the target.
                    sink.fallingBlock(new Location(world, bx + dx + 0.5, by, bz + dz + 0.5), material, ttl, owner, carry);
                }
            }
        }
    }
}
