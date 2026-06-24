package integrate.combat;

import java.lang.reflect.Method;
import java.util.function.BiPredicate;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * mcMMO integration (docs/decisions/0027): a "same party" predicate so StarEnchants treats mcMMO party
 * members as friendly — it applies no combat effects between two players in the same party (respecting the
 * party's friendly-fire-off expectation; wired into {@code CombatDispatch.friendlyFire}).
 *
 * <p>Reflective + fail-safe: mcMMO has no public maven artifact, so this binds {@code PartyAPI.inSameParty}
 * (a long-stable static) by reflection and degrades to "not friendly" (the status quo) if it is unavailable —
 * never throwing in the combat path.
 */
public final class Mcmmo {

    private Mcmmo() {
    }

    /**
     * A {@code (attacker, victim) → friendly?} predicate backed by mcMMO's {@code PartyAPI.inSameParty}, or a
     * constant {@code false} when mcMMO is absent / its API is not as expected.
     */
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
            return (attacker, victim) -> false; // mcMMO's API isn't what we expect — treat nobody as friendly
        }
    }
}
