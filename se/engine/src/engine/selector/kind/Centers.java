package engine.selector.kind;

import engine.selector.SelectorCtx;
import org.bukkit.Location;

/**
 * Shared centre resolution for the area selectors ({@link AoeSelector},
 * {@link NearestSelector}). The centre is the activation's explicit location (e.g. an
 * AoE centre the engine populated), falling back to the victim's then the actor's
 * location — so an area selector works whether the activation is a combat hit, a
 * block break, or a self-triggered effect.
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
