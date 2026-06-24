package engine.selector.kind;

import engine.selector.SelectorCtx;
import engine.selector.SelectorKind;
import engine.spec.SelectorSpec;
import java.util.List;
import org.bukkit.Location;
import schema.spec.D;

/**
 * {@code @Add{x,y,z}} — the activation location offset by a fixed {@code (x, y, z)} (docs/v3-directives.md
 * §A). A pure coordinate offset (no world read) — e.g. {@code SET_BLOCK:GLASS:@Add{y=2}} sets the block
 * two above the activation point.
 */
public final class AddSelector implements SelectorKind {

    static final SelectorSpec SPEC = SelectorSpec.of("ADD")
            .param("x", D.DOUBLE.def(0), "x offset in blocks")
            .param("y", D.DOUBLE.def(0), "y offset in blocks")
            .param("z", D.DOUBLE.def(0), "z offset in blocks")
            .doc("The activation location offset by (x, y, z).")
            .example("@Add{y=2}")
            .build();

    @Override
    public SelectorSpec spec() {
        return SPEC;
    }

    @Override
    public List<Location> resolveLocations(SelectorCtx ctx) {
        Location base = Centers.of(ctx);
        if (base == null) {
            return List.of();
        }
        return List.of(base.clone().add(ctx.dbl("x"), ctx.dbl("y"), ctx.dbl("z")));
    }
}
