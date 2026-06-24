package engine.selector.kind;

import engine.selector.SelectorCtx;
import engine.selector.SelectorKind;
import engine.spec.SelectorSpec;
import java.util.List;
import org.bukkit.Location;
import schema.spec.D;

/**
 * {@code @BlockInDistance{distance}} — the first solid block along the activator's line of sight, within a
 * configurable (typically longer) {@code distance} (docs/v3-directives.md §A; default 30). Same world
 * raytrace as {@code @Block} but reaching far beyond arm's length; empty if nothing is in sight.
 */
public final class BlockInDistanceSelector implements SelectorKind {

    static final SelectorSpec SPEC = SelectorSpec.of("BLOCKINDISTANCE")
            .param("distance", D.DOUBLE.min(0).def(30), "max look distance in blocks")
            .doc("The first solid block along the activator's line of sight, within distance.")
            .example("@BlockInDistance{distance=50}")
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
