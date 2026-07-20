package engine.selector.kind;

import engine.selector.SelectorCtx;
import java.util.Locale;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.Hoglin;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.entity.Shulker;
import org.bukkit.entity.Slime;

/**
 * Target filtering for area selectors. {@code filter} is a closed enum so an unknown value is rejected at
 * compile time. "Hostile" is {@link #isHostile} — {@link Monster} PLUS the historical non-Monster hostiles
 * (slimes, ghasts, phantoms, shulkers, hoglins, the ender dragon), all present at the 1.17.1 floor (the modern
 * {@code Enemy} marker is not, so it is deliberately unused). {@code ENEMIES}/{@code ALLIES} additionally
 * consult the {@link Allies} soft-hook; with no team bridge installed every other player is an enemy (vanilla
 * free-for-all PvP).
 */
final class Targets {

    private Targets() {
    }

    enum Filter {
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

        boolean accepts(Player actor, LivingEntity entity) {
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

    /**
     * Every hostile mob, cross-version: {@link Monster} (zombies, skeletons, blazes, guardians, withers,
     * zoglins, piglins/brutes, …) PLUS the historical non-Monster hostiles — {@link Slime} (and magma cubes,
     * its subclass), {@link Ghast}, {@link Phantom}, {@link Shulker}, {@link Hoglin} and the {@link EnderDragon}.
     * All exist at the 1.17.1 floor, so no version-gated {@code Enemy} reference (absent before 1.19.4).
     */
    static boolean isHostile(LivingEntity entity) {
        return entity instanceof Monster
                || entity instanceof Slime
                || entity instanceof Ghast
                || entity instanceof Phantom
                || entity instanceof Shulker
                || entity instanceof Hoglin
                || entity instanceof EnderDragon;
    }

    /** The validated {@code filter} argument (defaults to {@link Filter#ALL}). */
    static Filter of(SelectorCtx ctx) {
        return Filter.valueOf(ctx.args().str("filter").toUpperCase(Locale.ROOT));
    }
}
