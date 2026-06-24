package integrate.papi;

import java.util.function.BiFunction;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;

/**
 * PlaceholderAPI <em>passthrough</em> (docs/decisions/0027): resolves other plugins' {@code %…%} placeholders
 * inside StarEnchants player-facing chat messages, so an admin can write e.g. {@code %vault_eco_balance%} in a
 * lang.yml message and have it filled.
 *
 * <p>Bundled but SOFT: this class references the PAPI API, so {@link integrate.Integrations} only loads it
 * (via {@link #resolver()}) when PlaceholderAPI is present — otherwise the composition root installs an
 * identity resolver and StarEnchants never touches PAPI.
 *
 * <p>Scope is deliberately chat messages only. Item lore and menu text render from immutable state and are
 * cached on a content hash (see the item-data-model invariant), so injecting live, per-player placeholders
 * there would break that cache; lore passthrough is intentionally out of scope.
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
