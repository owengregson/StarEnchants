package engine.selector.kind;

import engine.selector.SelectorCtx;
import engine.selector.SelectorKind;
import engine.spec.SelectorSpec;
import java.util.List;
import org.bukkit.Location;

/**
 * {@code @Here} — the activation's own block location (docs/v3-directives.md §A). The default location
 * target for block effects ({@code SET_BLOCK}/{@code BREAK_BLOCK}), so the bare {@code SET_BLOCK:OBSIDIAN}
 * acts on the activation location exactly as before. The location analogue of {@code @Self}.
 */
public final class HereSelector implements SelectorKind {

    static final SelectorSpec SPEC = SelectorSpec.of("HERE")
            .doc("The activation block location itself — the default target of block effects.")
            .example("@Here")
            .build();

    @Override
    public SelectorSpec spec() {
        return SPEC;
    }

    @Override
    public List<Location> resolveLocations(SelectorCtx ctx) {
        Location loc = Centers.of(ctx);
        return loc == null ? List.of() : List.of(loc);
    }
}
