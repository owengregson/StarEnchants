package engine.selector.kind;

import engine.selector.SelectorCtx;
import java.util.Locale;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;

/**
 * Shared target filtering for the area selectors ({@link AoeSelector}, {@link NearestSelector}; v3.1 §A):
 * which living entities a region scan keeps. The {@code filter} argument is a closed enum so an unknown
 * value is rejected at compile time, and it defaults to {@link Filter#ALL} so the no-filter form is
 * unchanged. The {@code instanceof} checks use stable Bukkit interfaces ({@link Player}, {@link Monster})
 * present across the whole version range.
 */
final class Targets {

    private Targets() {
    }

    /** The set of living entities a selector keeps. */
    enum Filter {
        /** Any living entity. */
        ALL,
        /** Only players. */
        PLAYERS,
        /** Only hostile mobs ({@link Monster}). */
        MONSTERS,
        /** Any non-player living entity. */
        MOBS;

        boolean accepts(LivingEntity entity) {
            return switch (this) {
                case ALL -> true;
                case PLAYERS -> entity instanceof Player;
                case MONSTERS -> entity instanceof Monster;
                case MOBS -> !(entity instanceof Player);
            };
        }
    }

    /** The validated {@code filter} argument (defaulting to {@link Filter#ALL}). */
    static Filter of(SelectorCtx ctx) {
        return Filter.valueOf(ctx.args().str("filter").toUpperCase(Locale.ROOT));
    }
}
