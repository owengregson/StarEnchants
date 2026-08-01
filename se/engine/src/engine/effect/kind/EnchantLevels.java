package engine.effect.kind;

import java.util.function.BiFunction;
import org.bukkit.entity.Player;

/** Boot-installed item-layer bridge for mechanics that consume another worn enchant's level. */
public final class EnchantLevels {
    private EnchantLevels() {
    }

    private static volatile BiFunction<Player, String, Integer> resolver = (player, key) -> 0;

    public static void resolver(BiFunction<Player, String, Integer> next) {
        resolver = next == null ? (player, key) -> 0 : next;
    }

    public static int worn(Player player, String key) {
        try {
            return Math.max(0, resolver.apply(player, key));
        } catch (RuntimeException unreadable) {
            return 0;
        }
    }
}
