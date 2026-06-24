package engine.selector.kind;

import engine.selector.SelectorCtx;
import engine.selector.SelectorKind;
import engine.spec.SelectorSpec;
import java.util.List;
import org.bukkit.Location;
import schema.spec.D;

/**
 * {@code @Vein{limit}} — the locations of up to {@code limit} blocks contiguous with, and the same
 * material as, the activation block (docs/v3-directives.md §A; the vein-miner flood fill, default 64).
 * A world scan routed through the injected world-access seam so it is region-correct on Folia; empty when
 * there is no world (unit/synthetic context).
 */
public final class VeinSelector implements SelectorKind {

    static final SelectorSpec SPEC = SelectorSpec.of("VEIN")
            .param("limit", D.INT.min(1).def(64), "max blocks in the vein")
            .doc("Up to `limit` blocks contiguous with and matching the activation block (vein miner).")
            .example("@Vein{limit=32}")
            .build();

    @Override
    public SelectorSpec spec() {
        return SPEC;
    }

    @Override
    public List<Location> resolveLocations(SelectorCtx ctx) {
        Location base = BlockShapes.block(Centers.of(ctx));
        return base == null ? List.of() : List.copyOf(ctx.vein(base, ctx.integer("limit")));
    }
}
