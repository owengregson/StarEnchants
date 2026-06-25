package integrate.papi;

import java.util.function.BiFunction;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;

/**
 * PlaceholderAPI passthrough (docs/decisions/0027): fills other plugins' {@code %…%} placeholders inside
 * StarEnchants player-facing chat messages.
 *
 * <p>Bundled but SOFT: this class references the PAPI API, so {@link integrate.Integrations} loads it (via
 * {@link #resolver()}) only when PlaceholderAPI is present — otherwise an identity resolver is installed and
 * StarEnchants never touches PAPI.
 *
 * <p>Chat messages only by design: lore/menu text renders from immutable state cached on a content hash (the
 * item-data-model invariant), so live per-player placeholders there would break that cache.
 */
public final class PapiPassthrough {

    private PapiPassthrough() {
    }

    /** A resolver that fills {@code %…%} placeholders for the message's target player via PlaceholderAPI. */
    public static BiFunction<Player, String, String> resolver() {
        return (player, text) -> {
            if (player == null || text == null || text.indexOf('%') < 0) {
                return text; // no player context or no placeholder token — nothing to resolve
            }
            return PlaceholderAPI.setPlaceholders(player, text);
        };
    }
}
