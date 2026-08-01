package feature.combat;

import engine.effect.kind.EnchantLevels;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.entity.Player;

/** Shared source-exact normal/heroic Enchant Reflect routing for native offensive hooks. */
final class CosmicReflect {

    record Route(Player source, Player target) {
    }

    private CosmicReflect() {
    }

    static Route route(Player attacker, Player victim, int enchantLevel, int tier) {
        int normal = EnchantLevels.worn(victim, "enchants/enchant-reflect");
        int heroic = CosmicTierGate.tierSixPlusEnabled(victim)
                ? EnchantLevels.worn(victim, "enchants/heroic-enchant-reflect") : 0;
        int reflect = heroic > 0 && tier <= 7 && heroic >= enchantLevel ? heroic
                : normal > 0 && tier <= 5 && normal >= enchantLevel ? normal : 0;
        return reflect > 0 && ThreadLocalRandom.current().nextDouble() <= chance(reflect)
                ? new Route(victim, attacker)
                : new Route(attacker, victim);
    }

    static double chance(int level) {
        return 0.02 + 0.01 * (level / 3);
    }
}
