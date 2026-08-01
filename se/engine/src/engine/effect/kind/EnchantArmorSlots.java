package engine.effect.kind;

import java.util.Objects;
import java.util.function.BiFunction;
import org.bukkit.entity.Player;

/** Boot-installed item-layer bridge resolving the first worn armor slot carrying an enchant key. */
public final class EnchantArmorSlots {
    private EnchantArmorSlots() {
    }

    private static volatile BiFunction<Player, String, Integer> resolver = (player, key) -> -1;

    public static void resolver(BiFunction<Player, String, Integer> next) {
        resolver = next == null ? (player, key) -> -1 : next;
    }

    static int first(Player player, String key) {
        try {
            return resolver.apply(player, Objects.requireNonNull(key, "key"));
        } catch (RuntimeException unreadable) {
            return -1;
        }
    }
}
