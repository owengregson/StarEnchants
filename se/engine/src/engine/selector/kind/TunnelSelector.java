package engine.selector.kind;

import engine.selector.SelectorCtx;
import engine.selector.SelectorKind;
import engine.spec.SelectorSpec;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import schema.spec.D;

/**
 * {@code @Tunnel{depth}} — {@code depth} blocks directly ahead of the activation block along the actor's
 * dominant facing axis (docs/v3-directives.md §A; a 1-wide tunnel). Pure shape computation.
 */
public final class TunnelSelector implements SelectorKind {

    static final SelectorSpec SPEC = SelectorSpec.of("TUNNEL")
            .param("depth", D.INT.min(1).def(3), "blocks ahead along the look direction")
            .doc("The blocks directly ahead of the activation block, along the look direction.")
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
        List<Location> out = new ArrayList<>(depth);
        for (int i = 1; i <= depth; i++) {
            out.add(base.clone().add((double) forward[0] * i, (double) forward[1] * i, (double) forward[2] * i));
        }
        return out;
    }
}
