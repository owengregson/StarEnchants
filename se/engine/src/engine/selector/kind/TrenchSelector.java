package engine.selector.kind;

import engine.selector.SelectorCtx;
import engine.selector.SelectorKind;
import engine.spec.SelectorSpec;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import schema.spec.D;

/** {@code @Trench{radius}} — square of blocks perpendicular to the actor's facing, centred on the activation block. */
public final class TrenchSelector implements SelectorKind {

    static final SelectorSpec SPEC = SelectorSpec.of("TRENCH")
            .param("radius", D.INT.min(0).def(1), "half-width of the face (1 = 3x3)")
            .param("materials", D.materials().def(""), "keep only these block types (empty = every block)")
            .param("exclude-materials", D.materials().def(""), "drop these block types (empty = drop none)")
            .doc("The square of blocks perpendicular to the look direction, centred on the activation block. "
                    + "materials keeps only the listed block types and exclude-materials drops them, both "
                    + "written [STONE,DIRT] so the comma survives the selector body. A type on both lists is "
                    + "dropped.")
            .example("@Trench{radius=1}")
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
        int[][] axes = BlockShapes.perpendicular(forward);
        int[] u = axes[0];
        int[] v = axes[1];
        int r = ctx.integer("radius");
        List<Integer> materials = ctx.args().ids("materials");
        List<Integer> excluded = ctx.args().ids("exclude-materials");
        List<Location> out = new ArrayList<>((2 * r + 1) * (2 * r + 1));
        for (int du = -r; du <= r; du++) {
            for (int dv = -r; dv <= r; dv++) {
                Location at = base.clone().add(
                        (double) (u[0] * du + v[0] * dv),
                        (double) (u[1] * du + v[1] * dv),
                        (double) (u[2] * du + v[2] * dv));
                if (ctx.materialMatches(at, materials, excluded)) {
                    out.add(at);
                }
            }
        }
        return out;
    }
}
