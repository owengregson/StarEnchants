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
 */
public final class BoreSelector implements SelectorKind {

    static final SelectorSpec SPEC = SelectorSpec.of("BORE")
            .param("half-width", D.INT.min(0).def(1), "half the cross-section across (1 = 3 blocks wide)")
            .param("half-height", D.INT.min(0).def(1), "half the cross-section up and down (1 = 3 blocks tall)")
            .param("depth", D.INT.min(1).def(1), "layers into the face, counting the activation block's own")
            .param("materials", D.materials().def(""), "keep only these block types (empty = every block)")
            .param("exclude-materials", D.materials().def(""), "drop these block types (empty = drop none)")
            .doc("A half-width x half-height cross-section centred on the activation block, repeated depth "
                    + "layers into the mined face. depth=1 is a flat face; materials keeps only the listed "
                    + "block types and exclude-materials drops them, both written [STONE,DIRT] so the comma "
                    + "survives the selector body. A type on both lists is dropped.")
            .example("@Bore{half-width=1, half-height=1, depth=3, exclude-materials=[BEDROCK,OBSIDIAN]}")
            .build();

    @Override
    public SelectorSpec spec() {
        return SPEC;
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
        int depth = ctx.integer("depth");
        List<Integer> materials = ctx.args().ids("materials");
        List<Integer> excluded = ctx.args().ids("exclude-materials");
        List<Location> out = new ArrayList<>((2 * halfWidth + 1) * (2 * halfHeight + 1) * depth);
        for (int layer = 0; layer < depth; layer++) {
            for (int dw = -halfWidth; dw <= halfWidth; dw++) {
                for (int dh = -halfHeight; dh <= halfHeight; dh++) {
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
