package engine.effect.kind;

import java.util.function.ToIntFunction;
import org.bukkit.entity.Player;

/** Boot-installed item-layer bridge for Infinite Luck's 12.5% counter-roll per heroic armor piece. */
public final class HeroicArmorPieces {
    private HeroicArmorPieces() {
    }

    private static volatile ToIntFunction<Player> resolver = player -> 0;

    public static void resolver(ToIntFunction<Player> next) {
        resolver = next == null ? player -> 0 : next;
    }

    public static int count(Player player) {
        try {
            return player == null ? 0 : Math.max(0, resolver.applyAsInt(player));
        } catch (RuntimeException unreadable) {
            return 0;
        }
    }
}
