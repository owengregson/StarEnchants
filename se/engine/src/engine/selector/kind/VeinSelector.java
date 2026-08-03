package engine.selector.kind;

import engine.selector.SelectorCtx;
import engine.selector.SelectorKind;
import engine.spec.SelectorSpec;
import java.util.List;
import org.bukkit.Location;
import schema.spec.D;

/**
 * {@code @Vein{limit}} — vein-miner flood fill: up to {@code limit} blocks contiguous with and matching the
 * activation block. World scan via the world-access seam, so region-correct on Folia.
 */
public final class VeinSelector implements SelectorKind {

    static final SelectorSpec SPEC = SelectorSpec.of("VEIN")
            .param("limit", D.INT.min(1).def(64), "max blocks in the vein")
            .param("materials", D.materials().def(""), "only vein these block types (empty = whatever was struck)")
            .doc("Up to `limit` blocks contiguous with and matching the activation block (vein miner). materials "
                    + "restricts which struck blocks vein at all, written [IRON_ORE,GOLD_ORE] so the comma "
                    + "survives the selector body.")
            .example("@Vein{limit=32}")
            .build();

    @Override
    public SelectorSpec spec() {
        return SPEC;
    }

    @Override
    public List<Location> resolveLocations(SelectorCtx ctx) {
        Location base = BlockShapes.block(Centers.of(ctx));
        if (base == null) {
            return List.of();
        }
        // Gate the SEED, not each filled block: the fill is same-material by construction, so a struck block
        // outside the filter must vein nothing at all rather than vein and then be emptied out.
        List<Integer> materials = ctx.args().ids("materials");
        if (!ctx.materialMatches(base, materials)) {
            return List.of();
        }
        return List.copyOf(ctx.vein(base, ctx.integer("limit")));
    }
}
