package feature.menu;

import org.bukkit.ChatColor;
import platform.caps.Capabilities;

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

    /** Translate {@code &} codes and truncate to the server's safe title length. */
    public static String title(String legacy, Capabilities caps) {
        String colored = ChatColor.translateAlternateColorCodes('&', legacy == null ? "" : legacy);
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
        return cut;
    }
}
