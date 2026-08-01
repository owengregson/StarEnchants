package feature.combat;

import org.bukkit.World;
import org.bukkit.entity.Player;

/** Cosmic's global EListener rule: Soul, Heroic, and Mastery enchants do not function in The End. */
final class CosmicTierGate {

    private CosmicTierGate() {
    }

    static boolean tierSixPlusEnabled(Player owner) {
        return owner != null && tierSixPlusEnabled(owner.getWorld().getEnvironment());
    }

    static boolean tierSixPlusEnabled(World.Environment environment) {
        return environment != World.Environment.THE_END;
    }
}
