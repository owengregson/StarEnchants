package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import java.util.Locale;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;
import schema.spec.D;

/**
 * {@code TEMP_BLOCK} — place a temporary block shape that self-reverts after {@code ticks} (the Sink restores
 * the captured prior block, but only if it is still ours — overlap-safe, no ledger needed). Shapes: {@code POINT}
 * (one block at the target's feet, +{@code dy}), {@code FOOTPRINT} (a (2r+1)² square at feet level +{@code dy}),
 * {@code COLUMN} (a {@code height}-tall pillar, optionally {@code ahead} blocks in the target's facing). Used by
 * yeti (ice pillar + packed-ice footprint), fantasy (cobweb at feet), and devil (netherrack trail / floor).
 */
public final class TempBlockEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("TEMP_BLOCK")
            .param("shape", D.enumOf("POINT", "FOOTPRINT", "COLUMN").def("POINT"))
            .param("material", D.material())
            .param("ticks", D.TICKS.def(60))
            .param("radius", D.INT.range(0, 4).def(0))
            .param("height", D.INT.range(1, 8).def(1))
            .param("ahead", D.INT.range(0, 8).def(0))
            .param("dy", D.INT.range(-4, 4).def(0))
            .param("airOnly", D.BOOL.def(true))
            .target("who", T.VICTIM)
            .affinity(Affinity.REGION)
            .doc("Place a temporary block shape that reverts after `ticks`: shape POINT / FOOTPRINT (radius) / "
                    + "COLUMN (height, ahead in the target's facing), at feet level + dy. airOnly only replaces "
                    + "air (safe placement); a non-airOnly FOOTPRINT replaces only the solid ground under the feet "
                    + "(never air, so a trail can't scaffold); other shapes replace anything and restore on revert.")
            .example("{ TEMP_BLOCK: { shape: COLUMN, material: ICE, height: 2, ahead: 1, ticks: 60, who: \"@Attacker\" } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        String shape = ctx.str("shape").toUpperCase(Locale.ROOT);
        int material = ctx.integer("material");
        int ticks = ctx.integer("ticks");
        int radius = ctx.integer("radius");
        int height = ctx.integer("height");
        int ahead = ctx.integer("ahead");
        int dy = ctx.integer("dy");
        boolean airOnly = ctx.bool("airOnly");
        boolean footprint = "FOOTPRINT".equals(shape);
        // 0 = air only (safe); a non-air-only FOOTPRINT replaces ONLY the solid ground beneath the feet (mode 3),
        // so a moving trail can never let a player scaffold up by jumping into freshly-placed blocks; other shapes
        // replace anything (mode 2, captured + restored on revert).
        int mode = airOnly ? 0 : (footprint ? 3 : 2);
        for (LivingEntity who : ctx.targets("who")) {
            Location base = who.getLocation();
            World world = base.getWorld();
            if (world == null) {
                continue;
            }
            int bx = base.getBlockX();
            int by = base.getBlockY() + dy;
            int bz = base.getBlockZ();
            switch (shape) {
                case "FOOTPRINT" -> {
                    for (int dx = -radius; dx <= radius; dx++) {
                        for (int dz = -radius; dz <= radius; dz++) {
                            place(sink, world, bx + dx, by, bz + dz, material, ticks, mode);
                        }
                    }
                }
                case "COLUMN" -> {
                    int[] forward = forwardOffset(base, ahead);
                    for (int h = 0; h < height; h++) {
                        place(sink, world, bx + forward[0], by + h, bz + forward[1], material, ticks, mode);
                    }
                }
                default -> place(sink, world, bx, by, bz, material, ticks, mode); // POINT
            }
        }
    }

    private static void place(Sink sink, World world, int x, int y, int z, int material, int ticks, int mode) {
        sink.tempBlock(new Location(world, x, y, z), material, ticks, mode, false);
    }

    /** The horizontal block offset {@code ahead} blocks along the target's facing (forward by default). */
    private static int[] forwardOffset(Location base, int ahead) {
        if (ahead <= 0) {
            return new int[] {0, 0};
        }
        Vector dir = base.getDirection();
        double dx = dir == null ? 0.0 : dir.getX();
        double dz = dir == null ? 1.0 : dir.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0e-6) {
            dx = 0.0;
            dz = 1.0;
        } else {
            dx /= len;
            dz /= len;
        }
        return new int[] {(int) Math.round(dx * ahead), (int) Math.round(dz * ahead)};
    }
}
