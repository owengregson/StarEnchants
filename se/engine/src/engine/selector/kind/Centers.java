package engine.selector.kind;

import engine.selector.SelectorCtx;
import org.bukkit.Location;

/**
 * Centre resolution for area selectors ({@link AoeSelector}, {@link NearestSelector}). Falls back
 * activation location → victim → actor, so a selector works for a combat hit, a block break, or a
 * self-triggered effect alike.
 */
final class Centers {

    private Centers() {
    }

    /** The best available centre for an area scan, or {@code null} if none is known. */
    static Location of(SelectorCtx ctx) {
        if (ctx.location() != null) {
            return ctx.location();
        }
        if (ctx.victim() != null) {
            return ctx.victim().getLocation();
        }
        return ctx.actor() == null ? null : ctx.actor().getLocation();
    }
}
