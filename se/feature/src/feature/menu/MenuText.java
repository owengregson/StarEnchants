package feature.menu;

import item.render.Descriptions;
import java.util.List;
import org.bukkit.ChatColor;
import platform.caps.Capabilities;
import platform.text.Colors;

/**
 * Title rendering for menus (cross-version-item-api, paper-cross-version). The {@code String}-title
 * {@code createInventory} overload caps the title at 32 chars before 1.20 (a longer title throws/garbles on
 * the floor); 1.20+ lifted it. The cap counts the translated string the client receives, so colour codes are
 * translated first, then measured.
 */
public final class MenuText {

    static final int LEGACY_TITLE_LIMIT = 32;

    private MenuText() {
    }

    /**
     * A (possibly multi-line) description as lore lines, each prefixed with {@code defaultColor} so an
     * uncoloured line gets a sensible colour and a line carrying its own {@code &} code overrides it. Empty for
     * a blank description. Splitting here (not one lore entry with embedded {@code '\n'}) is what makes the
     * newlines render — item lore is a list of lines ({@link Descriptions}).
     */
    public static List<String> describe(String description, String defaultColor) {
        return Descriptions.lines(description).stream().map(line -> defaultColor + line).toList();
    }

    /** Translate {@code &} codes and truncate to the server's safe title length. */
    public static String title(String legacy, Capabilities caps) {
        String colored = Colors.translate(legacy == null ? "" : legacy);
        if (caps != null && caps.atLeast(1, 20, 0)) {
            return colored; // 1.20+ lifted the title-length cap
        }
        return truncate(colored, LEGACY_TITLE_LIMIT);
    }

    /**
     * Truncate {@code text} to at most {@code limit} characters without ending on a lone {@code §} (a colour
     * marker whose code digit was cut), which the client would render as a stray character.
     */
    static String truncate(String text, int limit) {
        if (text.length() <= limit) {
            return text;
        }
        String cut = text.substring(0, limit);
        if (cut.charAt(cut.length() - 1) == ChatColor.COLOR_CHAR) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return dropTruncatedHexRun(cut);
    }

    /**
     * A cut landing inside a {@code §x§R§R§G§G§B§B} run (ADR-0062) leaves a PARTIAL hex colour whose leftover
     * pairs an old client renders as ordinary colour codes — drop the whole partial run. A complete six-pair
     * run, or a short run followed by other text (authored that way, not truncated), is kept.
     */
    private static String dropTruncatedHexRun(String cut) {
        int x = Math.max(cut.lastIndexOf("§x"), cut.lastIndexOf("§X"));
        if (x < 0) {
            return cut;
        }
        int end = x + 2;
        int pairs = 0;
        while (pairs < 6 && end + 1 < cut.length() && cut.charAt(end) == ChatColor.COLOR_CHAR
                && Character.digit(cut.charAt(end + 1), 16) >= 0) {
            pairs++;
            end += 2;
        }
        return pairs < 6 && end >= cut.length() ? cut.substring(0, x) : cut;
    }
}
