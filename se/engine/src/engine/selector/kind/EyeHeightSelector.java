package engine.selector.kind;

import engine.selector.SelectorCtx;
import engine.selector.SelectorKind;
import engine.spec.SelectorSpec;
import java.util.List;
import org.bukkit.Location;

/**
 * {@code @EyeHeight} — the actor's eye location (docs/v3-directives.md §A). Pure read of the firing-thread
 * actor, no world scan.
 */
public final class EyeHeightSelector implements SelectorKind {

    static final SelectorSpec SPEC = SelectorSpec.of("EYEHEIGHT")
            .doc("The activator's eye location (their position at eye level).")
            .example("@EyeHeight")
            .build();

    @Override
    public SelectorSpec spec() {
        return SPEC;
    }

    @Override
    public List<Location> resolveLocations(SelectorCtx ctx) {
        return ctx.actor() == null ? List.of() : List.of(ctx.actor().getEyeLocation());
    }
}
