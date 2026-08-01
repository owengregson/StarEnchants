package engine.effect.kind;

import java.util.function.BiPredicate;
import org.bukkit.entity.Player;

/** Boot-installed item-layer bridge for condition facts that depend on a currently worn mask component. */
public final class ActiveMasks {
    private ActiveMasks() {
    }

    private static volatile BiPredicate<Player, String> resolver = (player, key) -> false;

    public static void resolver(BiPredicate<Player, String> next) {
        resolver = next == null ? (player, key) -> false : next;
    }

    public static boolean has(Player player, String key) {
        try {
            return player != null && key != null && resolver.test(player, key);
        } catch (RuntimeException unreadable) {
            return false;
        }
    }
}
