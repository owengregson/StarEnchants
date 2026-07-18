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
import platform.caps.Regions;
import schema.spec.D;

/**
 * {@code TEMP_BLOCK} — place a temporary block shape that self-reverts after {@code ticks} (the Sink layers
 * overlapping placements through its shared {@code TempBlockLedger}, so a stacked trail/floor compounds and the
 * final revert restores the true original, never an intermediate temp block). Shapes: {@code POINT}
 * (one block at the target's feet, +{@code dy}), {@code FOOTPRINT} (a (2r+1)² square at feet level +{@code dy};
 * radius 0 routes to the sink's {@code TrailWalker} snake so consecutive stamps join into a gapless 4-connected
 * path), {@code COLUMN} (a {@code height}-tall pillar, optionally {@code ahead} blocks in the target's facing).
 * Used by yeti (ice pillar + packed-ice footprint), fantasy (cobweb at feet), and devil (netherrack trail / floor).
 */
public final class TempBlockEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("TEMP_BLOCK")
            .param("shape", D.enumOf("POINT", "FOOTPRINT", "COLUMN", "BOX").def("POINT"))
            .param("material", D.material())
            .param("material2", D.material().optional())
            .param("material3", D.material().optional())
            .param("material4", D.material().optional())
            .param("ticks", D.TICKS.def(60))
            .param("radius", D.INT.range(0, 4).def(0))
            .param("width", D.INT.range(1, 8).def(3))
            .param("height", D.INT.range(1, 8).def(1))
            .param("depth", D.INT.range(1, 8).def(3))
            .param("ahead", D.INT.range(0, 8).def(0))
            .param("dy", D.INT.range(-4, 4).def(0))
            .param("airOnly", D.BOOL.def(true))
            .target("who", T.VICTIM)
            .affinity(Affinity.REGION)
            .doc("Place a temporary block shape that reverts after `ticks`: shape POINT / FOOTPRINT (radius) / "
                    + "COLUMN (height, ahead in the target's facing) / BOX (width × height × depth filled volume "
                    + "horizontally centred on the target — the ADR-0052 Spider webs), at feet level + dy. airOnly "
                    + "only replaces air (safe placement); a non-airOnly FOOTPRINT replaces only the solid ground "
                    + "under the feet (never air, so a trail can't scaffold); other shapes replace anything and "
                    + "restore on revert. A radius-0 FOOTPRINT trails as a snake — consecutive stamps join into a "
                    + "gapless, 4-connected footprint path even at sprint speed and on diagonals. Give material2/3/4 "
                    + "to place a mixed palette: each block independently picks a material from a deterministic "
                    + "per-block hash of its coordinates — a noisy, random-looking scatter (re-placing the same block "
                    + "always picks the same material). A BOX is always single-material (palette[0]).")
            .example("{ TEMP_BLOCK: { shape: COLUMN, material: ICE, height: 2, ahead: 1, ticks: 60, who: \"@Attacker\" } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        String shape = ctx.str("shape").toUpperCase(Locale.ROOT);
        int[] palette = palette(ctx);
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
            Location base;
            try {
                base = who.getLocation(); // T.VICTIM, but @Attacker on a DEFENSE trigger can be a cross-region shooter (ADR-0043)
            } catch (RuntimeException unreadable) {
                Regions.swallowed("TempBlockEffect.target", unreadable);
                continue;
            }
            World world = base.getWorld();
            if (world == null) {
                continue;
            }
            int bx = base.getBlockX();
            int by = base.getBlockY() + dy;
            int bz = base.getBlockZ();
            switch (shape) {
                case "FOOTPRINT" -> {
                    if (radius == 0) {
                        // The snake: hand the walker's current cell to the sink, which joins it to the previous
                        // cell as a 4-connected trail (no gaps at sprint speed, clean L-steps on diagonals). The
                        // effect stays stateless — the sink owns the path memory (the MARK_ZONE precedent). The
                        // trail API is single-material, so a snake trail always uses palette[0] (the base material).
                        sink.tempBlockTrail(ctx.sourceDefId(), who.getUniqueId(),
                                new Location(world, bx, by, bz), palette[0], ticks);
                    } else {
                        for (int dx = -radius; dx <= radius; dx++) {
                            for (int dz = -radius; dz <= radius; dz++) {
                                place(sink, world, bx + dx, by, bz + dz, palette, ticks, mode);
                            }
                        }
                    }
                }
                case "COLUMN" -> {
                    int[] forward = forwardOffset(base, ahead);
                    for (int h = 0; h < height; h++) {
                        place(sink, world, bx + forward[0], by + h, bz + forward[1], palette, ticks, mode);
                    }
                }
                case "BOX" ->
                    // One 3D intent: the sink owns per-tile region re-keying (a box may straddle a boundary).
                    // A BOX is always entity-centred (the Spider box) → register it as a confining structure
                    // (ADR-0071 TRAP_BREAK) so Turnkey can early-restore it.
                    sink.tempBox(new Location(world, bx, by, bz), palette[0],
                            ctx.integer("width"), height, ctx.integer("depth"), ticks, mode, who.getUniqueId());
                default -> {
                    // POINT: a block in the target's own cell (dy >= 0, the Fantasy web) is a confining trap —
                    // register it via the 6-arg overload; a POINT below the feet (dy < 0) is floor paint, plain.
                    if (dy >= 0) {
                        sink.tempBlock(new Location(world, bx, by, bz), materialAt(palette, bx, bz), ticks, mode,
                                false, who.getUniqueId());
                    } else {
                        place(sink, world, bx, by, bz, palette, ticks, mode);
                    }
                }
            }
        }
    }

    /** The placement palette in authored order: [material, material2.., material3.., material4..], present ones only. */
    private static int[] palette(EffectCtx ctx) {
        int[] out = new int[4];
        int n = 0;
        out[n++] = ctx.integer("material");
        if (ctx.args().has("material2")) {
            out[n++] = ctx.integer("material2");
        }
        if (ctx.args().has("material3")) {
            out[n++] = ctx.integer("material3");
        }
        if (ctx.args().has("material4")) {
            out[n++] = ctx.integer("material4");
        }
        return n == out.length ? out : java.util.Arrays.copyOf(out, n);
    }

    private static void place(Sink sink, World world, int x, int y, int z, int[] palette, int ticks, int mode) {
        sink.tempBlock(new Location(world, x, y, z), materialAt(palette, x, z), ticks, mode, false);
    }

    /**
     * The palette material for the block at {@code (x, z)}: a single-material palette is byte-identical to the
     * plain material; a multi-material palette picks {@code palette[positiveHash(x, z) % size]} — an independent
     * per-block draw, so adjacent blocks scatter (noisy, not patched). The hash is a pure integer mix (no
     * {@code Random}), so the same coordinate always yields the same material and reverts/trail fingerprints stay stable.
     */
    private static int materialAt(int[] palette, int x, int z) {
        if (palette.length == 1) {
            return palette[0];
        }
        return palette[Math.floorMod(blockHash(x, z), palette.length)];
    }

    /** A cheap deterministic spatial mix of a block coordinate (the classic 73856093/19349663 hash, finalized). */
    private static int blockHash(int x, int z) {
        int h = x * 73856093 ^ z * 19349663;
        h ^= h >>> 16;
        h *= 0x7feb352d;
        h ^= h >>> 15;
        return h;
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
