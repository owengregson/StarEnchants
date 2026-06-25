package integrate.papi;

import java.util.Locale;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * The {@code %starenchants_…%} PlaceholderAPI expansion (docs/decisions/0027): surfaces StarEnchants
 * player state to scoreboards/holograms/chat through PAPI.
 *
 * <p>Bundled but SOFT: the PAPI API is {@code compileOnly} and {@link integrate.Integrations} loads/registers
 * this class only when PlaceholderAPI is present. It holds no StarEnchants types — runtime state is read
 * through JDK-typed accessors the composition root supplies — so PAPI never loads StarEnchants internals.
 *
 * <p>Placeholders:
 * <ul>
 *   <li>{@code %starenchants_soulmode%} — {@code on}/{@code off} (the player's soul-mode toggle);</li>
 *   <li>{@code %starenchants_souls%} — the soul balance of the player's active gem (0 when soul mode is off).</li>
 * </ul>
 * Unknown placeholders return {@code null} (PAPI then leaves the raw token).
 */
public final class SePlaceholderExpansion extends PlaceholderExpansion {

    private final String version;
    private final Predicate<Player> soulMode;
    private final ToIntFunction<Player> souls;

    private SePlaceholderExpansion(String version, Predicate<Player> soulMode, ToIntFunction<Player> souls) {
        this.version = version;
        this.soulMode = soulMode;
        this.souls = souls;
    }

    /**
     * Construct + register the expansion; returns whether registration succeeded. Called from the registrar
     * only when PlaceholderAPI is present, so referencing this class (and thus PAPI) is gated.
     */
    public static boolean install(String version, Predicate<Player> soulMode, ToIntFunction<Player> souls) {
        return new SePlaceholderExpansion(version, soulMode, souls).register();
    }

    @Override
    public String getIdentifier() {
        return "starenchants";
    }

    @Override
    public String getAuthor() {
        return "StarEnchants";
    }

    @Override
    public String getVersion() {
        return version;
    }

    /** Survive a {@code /papi reload} — StarEnchants registers this once at boot, not on PAPI's lifecycle. */
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offline, String params) {
        if (offline == null) {
            return "";
        }
        return resolve(params, offline.getPlayer(), soulMode, souls);
    }

    /**
     * The pure placeholder lookup, split out for unit testing without constructing a PAPI expansion. A null
     * {@code player} (offline) reads the off/zero defaults; an unknown token returns {@code null} (PAPI then
     * leaves the raw token). Case-insensitive.
     */
    static String resolve(String params, Player player, Predicate<Player> soulMode, ToIntFunction<Player> souls) {
        if (params == null) {
            return "";
        }
        return switch (params.toLowerCase(Locale.ROOT)) {
            case "soulmode" -> player != null && soulMode.test(player) ? "on" : "off";
            case "souls" -> player != null ? Integer.toString(souls.applyAsInt(player)) : "0";
            default -> null;
        };
    }
}
