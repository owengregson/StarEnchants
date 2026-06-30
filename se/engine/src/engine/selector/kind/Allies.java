package engine.selector.kind;

import java.util.Objects;
import java.util.function.BiPredicate;
import org.bukkit.entity.Player;

/**
 * Soft-hook deciding whether two players are ALLIED (same team / party / faction), consulted by the
 * {@code ENEMIES}/{@code ALLIES} area filters ({@link Targets.Filter}). Boot installs a bridge to whatever
 * team plugin is present; with none installed the default treats everyone as an enemy — vanilla free-for-all
 * PvP, the safe assumption for an AoE strike. Kept off the engine API surface (one static volatile hook,
 * mirroring {@code FactPopulator.entityTypeResolver}) so engine-core never references a team plugin.
 */
public final class Allies {

    private Allies() {
    }

    /** Default: no alliances — every other player is an enemy. */
    private static volatile BiPredicate<Player, Player> hook = (a, b) -> false;

    /** Install the alliance bridge (boot-time). A {@code null} resolver resets to the no-alliance default. */
    public static void resolver(BiPredicate<Player, Player> resolver) {
        hook = resolver == null ? (a, b) -> false : resolver;
    }

    /** Whether {@code a} and {@code b} are allied (never the same player; a faulty bridge degrades to "not allied"). */
    public static boolean allied(Player a, Player b) {
        if (a == null || b == null || Objects.equals(a, b)) {
            return false;
        }
        try {
            return hook.test(a, b);
        } catch (RuntimeException faultyBridge) {
            return false;
        }
    }
}
