package engine.selector.kind;

import engine.selector.SelectorCtx;
import java.util.Locale;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;

/**
 * Target filtering for area selectors ({@link AoeSelector}, {@link NearestSelector}; v3.1 §A). {@code filter}
 * is a closed enum so an unknown value is rejected at compile time. The {@code instanceof} checks use
 * {@link Player}/{@link Monster}, stable Bukkit interfaces across the whole version range.
 */
final class Targets {

    private Targets() {
    }

    enum Filter {
        ALL,
        PLAYERS,
        /** Hostile mobs ({@link Monster}). */
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

    /** The validated {@code filter} argument (defaults to {@link Filter#ALL}). */
    static Filter of(SelectorCtx ctx) {
        return Filter.valueOf(ctx.args().str("filter").toUpperCase(Locale.ROOT));
    }
}
