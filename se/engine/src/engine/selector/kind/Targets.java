package engine.selector.kind;

import engine.selector.SelectorCtx;
import java.util.Locale;
import java.util.Set;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;

/**
 * Target filtering for area selectors. {@code filter} is a closed enum so an unknown value is rejected at
 * compile time. "Hostile" is {@link #isHostile} — {@link Monster} PLUS the non-Monster hostiles: slimes,
 * ghasts and the ender dragon by class (present at the 1.8 floor the shared core ALSO compiles against), and
 * shulkers/phantoms/hoglins by {@code EntityType} name (absent from the 1.8 API, so a class reference would not
 * compile). The modern {@code Enemy} marker (1.19.4+) is deliberately unused for the same reason.
 * {@code ENEMIES}/{@code ALLIES} additionally consult the {@link Allies} soft-hook; with no team bridge
 * installed every other player is an enemy (vanilla free-for-all PvP).
 */
final class Targets {

    private Targets() {
    }

    /** A validated {@code filter}: one {@link Filter}, or a conjunction of several. */
    interface Match {
        boolean accepts(Player actor, LivingEntity entity);
    }

    enum Filter implements Match {
        ALL,
        PLAYERS,
        /** Every hostile mob (see {@link #isHostile}). */
        MONSTERS,
        /** Any non-player living entity. */
        MOBS,
        /** Every hostile mob + players the {@link Allies} hook does not consider allied to the actor. */
        ENEMIES,
        /** Players the {@link Allies} hook considers allied to the actor (never the actor itself). */
        ALLIES;

        @Override
        public boolean accepts(Player actor, LivingEntity entity) {
            return switch (this) {
                case ALL -> true;
                case PLAYERS -> entity instanceof Player;
                case MONSTERS -> isHostile(entity);
                case MOBS -> !(entity instanceof Player);
                case ENEMIES -> isHostile(entity)
                        || (entity instanceof Player p && !Allies.allied(actor, p));
                case ALLIES -> entity instanceof Player p && Allies.allied(actor, p);
            };
        }
    }

    /** Hostile mob types absent from the 1.8 API (Shulker 1.9, Phantom 1.13, Hoglin 1.16) — matched by
     * {@code EntityType} NAME so the shared core still compiles against the legacy tree (the legacy-1.8.9 rule).
     * Enum constant names are stable across the modern range; an unmatched name just fails closed. */
    private static final Set<String> NEWER_HOSTILE_TYPES = Set.of("SHULKER", "PHANTOM", "HOGLIN");

    /**
     * Every hostile mob, cross-version: {@link Monster} (zombies, skeletons, blazes, guardians, withers,
     * zoglins, piglins/brutes, …) PLUS the non-Monster hostiles — {@link Slime} (and magma cubes, its subclass),
     * {@link Ghast} and the {@link EnderDragon} by class, and shulkers/phantoms/hoglins by {@code EntityType}
     * name (their classes are absent from the 1.8 API the shared core also compiles against; {@code Enemy} is
     * 1.19.4+ and equally unavailable).
     */
    static boolean isHostile(LivingEntity entity) {
        if (entity instanceof Monster || entity instanceof Slime || entity instanceof Ghast
                || entity instanceof EnderDragon) {
            return true;
        }
        EntityType type = entity.getType(); // only reached for a non-Monster class; null-guarded for test mocks
        return type != null && NEWER_HOSTILE_TYPES.contains(type.name());
    }

    /**
     * The validated {@code filter} argument (defaults to {@link Filter#ALL}). A {@code A+B} value is a
     * CONJUNCTION — {@code ENEMIES+PLAYERS} is the player-only hostile payload neither part expresses alone.
     * The single-value path allocates nothing; only a composed filter builds an array, once per resolve.
     */
    static Match of(SelectorCtx ctx) {
        String raw = ctx.args().str("filter");
        return raw.indexOf('+') < 0 ? single(raw) : conjunction(raw);
    }

    private static Filter single(String value) {
        return Filter.valueOf(value.toUpperCase(Locale.ROOT));
    }

    private static Match conjunction(String raw) {
        String[] parts = raw.split("\\+");
        Filter[] filters = new Filter[parts.length];
        for (int i = 0; i < parts.length; i++) {
            filters[i] = single(parts[i].trim());
        }
        return (actor, entity) -> {
            for (Filter f : filters) {
                if (!f.accepts(actor, entity)) {
                    return false;
                }
            }
            return true;
        };
    }
}
