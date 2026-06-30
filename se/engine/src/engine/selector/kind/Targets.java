package engine.selector.kind;

import engine.selector.SelectorCtx;
import java.util.Locale;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;

/**
 * Target filtering for area selectors. {@code filter} is a closed enum so an unknown value is rejected at
 * compile time. The {@code instanceof} checks use {@link Player}/{@link Monster} — stable across the range.
 * {@code ENEMIES}/{@code ALLIES} additionally consult the {@link Allies} soft-hook; with no team bridge
 * installed every other player is an enemy (vanilla free-for-all PvP).
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
        MOBS,
        /** Hostile mobs + players the {@link Allies} hook does not consider allied to the actor. */
        ENEMIES,
        /** Players the {@link Allies} hook considers allied to the actor (never the actor itself). */
        ALLIES;

        boolean accepts(Player actor, LivingEntity entity) {
            return switch (this) {
                case ALL -> true;
                case PLAYERS -> entity instanceof Player;
                case MONSTERS -> entity instanceof Monster;
                case MOBS -> !(entity instanceof Player);
                case ENEMIES -> entity instanceof Monster
                        || (entity instanceof Player p && !Allies.allied(actor, p));
                case ALLIES -> entity instanceof Player p && Allies.allied(actor, p);
            };
        }
    }

    /** The validated {@code filter} argument (defaults to {@link Filter#ALL}). */
    static Filter of(SelectorCtx ctx) {
        return Filter.valueOf(ctx.args().str("filter").toUpperCase(Locale.ROOT));
    }
}
