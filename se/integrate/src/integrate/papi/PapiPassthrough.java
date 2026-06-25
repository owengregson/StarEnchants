package integrate.papi;

import java.util.function.BiFunction;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;

/**
 * PlaceholderAPI passthrough (docs/decisions/0027): fills other plugins' {@code %…%} placeholders inside
 * StarEnchants player-facing chat messages. Chat only by design — lore/menu text renders from state cached on
 * a content hash (item-data-model), so live per-player placeholders there would break that cache.
 */
public final class PapiPassthrough {

    private PapiPassthrough() {
    }

    /** A resolver that fills {@code %…%} placeholders for the message's target player via PlaceholderAPI. */
    public static BiFunction<Player, String, String> resolver() {
        return (player, text) -> {
            if (player == null || text == null || text.indexOf('%') < 0) {
                return text;
            }
            return PlaceholderAPI.setPlaceholders(player, text);
        };
    }
}
