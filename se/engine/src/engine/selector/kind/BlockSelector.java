package engine.selector.kind;

import engine.selector.SelectorCtx;
import engine.selector.SelectorKind;
import engine.spec.SelectorSpec;
import java.util.List;
import org.bukkit.Location;
import schema.spec.D;

/**
 * {@code @Block{distance}} — first solid block the actor is looking at, within {@code distance}. Raytrace
 * via the world-access seam, so region-correct on Folia.
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
