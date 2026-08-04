package engine.selector.kind;

import engine.selector.SelectorCtx;
import engine.selector.SelectorKind;
import engine.spec.SelectorSpec;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import schema.spec.D;

/**
 * {@code @Bore{half-width, half-height, depth, materials}} — the face-oriented box: a cross-section centred on
 * the activation block, repeated {@code depth} layers into the face being mined. Generalises the family rather
 * than forking it: {@code depth=1} is exactly {@code @Trench}, and a zero cross-section is {@code @Tunnel}
 * including its own first block. Orientation is the mined face, read as {@link BlockShapes#facing} — the same
 * dominant look axis the whole family uses.
 *
 * <p>The cross-section may be ASYMMETRIC ({@code left}/{@code right}/{@code up}/{@code down}), which is not a
 * convenience: a {@code -half..+half} loop always spans an ODD number of blocks, so 4x4 and 6x6 sections — the
 * even rungs of the excavation ladders — are unreachable from the half params at any value.
 */
public final class BoreSelector implements SelectorKind {

    static final SelectorSpec SPEC = SelectorSpec.of("BORE")
            .param("half-width", D.INT.min(0).def(1), "half the cross-section across (1 = 3 blocks wide)")
            .param("half-height", D.INT.min(0).def(1), "half the cross-section up and down (1 = 3 blocks tall)")
            .param("depth", D.INT.min(1).def(1), "layers into the face, counting the activation block's own")
            // Per-side extents: -1 means "take the symmetric half above". A cross-section of an EVEN width or
            // height is unreachable from a -half..+half loop at any half value, and the excavation ladders
            // step through 4x4 and 6x6 on their way to 5x5 and 7x7.
            .param("left", D.INT.min(-1).def(-1), "blocks left of centre; -1 = half-width")
            .param("right", D.INT.min(-1).def(-1), "blocks right of centre; -1 = half-width")
            .param("up", D.INT.min(-1).def(-1), "blocks above centre; -1 = half-height")
            .param("down", D.INT.min(-1).def(-1), "blocks below centre; -1 = half-height")
            .param("materials", D.materials().def(""), "keep only these block types (empty = every block)")
            .param("exclude-materials", D.materials().def(""), "drop these block types (empty = drop none)")
            .doc("A half-width x half-height cross-section centred on the activation block, repeated depth "
                    + "layers into the mined face. depth=1 is a flat face; materials keeps only the listed "
                    + "block types and exclude-materials drops them, both written [STONE,DIRT] so the comma "
                    + "survives the selector body. A type on both lists is dropped. left/right/up/down "
                    + "override their axis's half-* for an ASYMMETRIC cross-section — the only way to reach an "
                    + "even width or height (left=1, right=2 is 4 blocks across).")
            .example("@Bore{half-width=1, half-height=1, depth=3, exclude-materials=[BEDROCK,OBSIDIAN]}")
            .build();

    @Override
    public SelectorSpec spec() {
        return SPEC;
    }

    /** A per-side extent, or the symmetric half when the author left it at the {@code -1} sentinel. */
    private static int extent(SelectorCtx ctx, String name, int symmetric) {
        int authored = ctx.integer(name);
        return authored < 0 ? symmetric : authored;
    }

    @Override
    public List<Location> resolveLocations(SelectorCtx ctx) {
        Location base = BlockShapes.block(Centers.of(ctx));
        int[] forward = BlockShapes.facing(ctx.actor());
        if (base == null || forward == null) {
            return List.of();
        }
        int[][] axes = BlockShapes.widthHeight(forward);
        int[] w = axes[0];
        int[] h = axes[1];
        int halfWidth = ctx.integer("half-width");
        int halfHeight = ctx.integer("half-height");
        int left = extent(ctx, "left", halfWidth);
        int right = extent(ctx, "right", halfWidth);
        int down = extent(ctx, "down", halfHeight);
        int up = extent(ctx, "up", halfHeight);
        int depth = ctx.integer("depth");
        List<Integer> materials = ctx.args().ids("materials");
        List<Integer> excluded = ctx.args().ids("exclude-materials");
        List<Location> out = new ArrayList<>((left + right + 1) * (down + up + 1) * depth);
        for (int layer = 0; layer < depth; layer++) {
            for (int dw = -left; dw <= right; dw++) {
                for (int dh = -down; dh <= up; dh++) {
                    Location at = base.clone().add(
                            (double) (forward[0] * layer + w[0] * dw + h[0] * dh),
                            (double) (forward[1] * layer + w[1] * dw + h[1] * dh),
                            (double) (forward[2] * layer + w[2] * dw + h[2] * dh));
                    if (ctx.materialMatches(at, materials, excluded)) {
                        out.add(at);
                    }
                }
            }
        }
        return out;
    }
}
