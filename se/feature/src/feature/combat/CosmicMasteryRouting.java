package feature.combat;

import engine.effect.kind.ActiveMasks;
import engine.effect.kind.ActiveSets;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;
import org.bukkit.entity.Player;

/** Shared Rift/Dragon Slayer dispatch used before every offensive Cosmic mastery proc. */
final class CosmicMasteryRouting {

    private static final String DRAGON_SLAYER = "sets/dragon-slayer";
    private static final String RIFT_MASK = "masks/rift-mask";

    record Route(Player source, Player target, boolean blocked) {
    }

    private CosmicMasteryRouting() {
    }

    static Route route(Player source, Player target, int level) {
        return route(source, target, level, () -> ThreadLocalRandom.current().nextDouble());
    }

    static Route route(Player source, Player target, int level, DoubleSupplier random) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(random, "random");

        if (ActiveMasks.has(target, RIFT_MASK) && random.getAsDouble() <= 0.5) {
            return new Route(source, target, true);
        }
        int reflectLevel = ActiveSets.has(target, DRAGON_SLAYER) ? 10 : 0;
        return routeForReflectLevel(source, target, level, reflectLevel, random);
    }

    static Route routeForReflectLevel(
            Player source, Player target, int level, int reflectLevel, DoubleSupplier random) {
        if (reflectLevel < level) {
            return new Route(source, target, false);
        }
        if (random.getAsDouble() <= reflectChance(reflectLevel)) {
            return new Route(target, source, false);
        }
        return new Route(source, target, random.getAsDouble() <= negateChance(reflectLevel));
    }

    static double reflectChance(int reflectLevel) {
        return 0.02 + 0.0267 * (reflectLevel / 3);
    }

    static double negateChance(int reflectLevel) {
        return 0.01 + 0.0833 * (reflectLevel / 3);
    }
}
