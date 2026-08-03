package engine.selector.kind;

import engine.selector.SelectorCtx;
import engine.selector.SelectorKind;
import engine.spec.SelectorSpec;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import schema.spec.D;

/** {@code @Tunnel{depth}} — {@code depth} blocks directly ahead along the actor's dominant facing axis (1-wide). */
public final class TunnelSelector implements SelectorKind {

    static final SelectorSpec SPEC = SelectorSpec.of("TUNNEL")
            .param("depth", D.INT.min(1).def(3), "blocks ahead along the look direction")
            .param("materials", D.materials().def(""), "keep only these block types (empty = every block)")
            .doc("The blocks directly ahead of the activation block, along the look direction. materials keeps "
                    + "only the listed block types, written [STONE,DIRT] so the comma survives the selector body.")
            .example("@Tunnel{depth=4}")
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
        int depth = ctx.integer("depth");
        List<Integer> materials = ctx.args().ids("materials");
        List<Location> out = new ArrayList<>(depth);
        for (int i = 1; i <= depth; i++) {
            Location at = base.clone().add(
                    (double) forward[0] * i, (double) forward[1] * i, (double) forward[2] * i);
            if (ctx.materialMatches(at, materials)) {
                out.add(at);
            }
        }
        return out;
    }
}
