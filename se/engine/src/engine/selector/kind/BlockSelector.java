package engine.selector.kind;

import engine.selector.SelectorCtx;
import engine.selector.SelectorKind;
import engine.spec.SelectorSpec;
import java.util.List;
import org.bukkit.Location;
import schema.spec.D;

/**
 * {@code @Block{distance}} — the first solid block the activator is looking at, within {@code distance}
 * blocks (docs/v3-directives.md §A; default 5, the reach of a normal look). A world raytrace routed
 * through the injected world-access seam so it is region-correct on Folia; empty if nothing is in sight.
 */
public final class BlockSelector implements SelectorKind {

    static final SelectorSpec SPEC = SelectorSpec.of("BLOCK")
            .param("distance", D.DOUBLE.min(0).def(5), "max look distance in blocks")
            .doc("The first solid block the activator is looking at, within distance.")
            .example("@Block")
            .build();

    @Override
    public SelectorSpec spec() {
        return SPEC;
    }

    @Override
    public List<Location> resolveLocations(SelectorCtx ctx) {
        Location block = ctx.targetBlock(ctx.dbl("distance"));
        return block == null ? List.of() : List.of(block);
    }
}
