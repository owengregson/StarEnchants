package integrate.combat;

import java.lang.reflect.Method;
import java.util.function.BiPredicate;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * mcMMO integration (docs/decisions/0027): a "same party" predicate so no combat effects apply between two
 * players in the same party. Reflective (mcMMO has no public maven artifact) + fail-safe.
 */
public final class Mcmmo {

    private Mcmmo() {
    }

    /** Backed by mcMMO's {@code PartyAPI.inSameParty}, or constant {@code false} when mcMMO is absent/unexpected. */
    public static BiPredicate<Player, Player> sameParty(Plugin plugin) {
        if (plugin.getServer().getPluginManager().getPlugin("mcMMO") == null) {
            return (attacker, victim) -> false;
        }
        try {
            Class<?> partyApi = Class.forName("com.gmail.nossr50.api.PartyAPI");
            Method inSameParty = partyApi.getMethod("inSameParty", Player.class, Player.class);
            return (attacker, victim) -> {
                try {
                    return Boolean.TRUE.equals(inSameParty.invoke(null, attacker, victim));
                } catch (ReflectiveOperationException failed) {
                    return false; // fail-safe: never break a hit over a party lookup
                }
            };
        } catch (ReflectiveOperationException unavailable) {
            return (attacker, victim) -> false; // unexpected mcMMO API — treat nobody as friendly
        }
    }
}
