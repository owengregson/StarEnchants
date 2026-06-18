package feature.menu;

import org.bukkit.ChatColor;
import platform.caps.Capabilities;

/**
 * Title rendering for menus: translate legacy {@code &} colour codes and enforce the cross-version
 * inventory-title length limit (cross-version-item-api, paper-cross-version).
 *
 * <p>The {@code String}-title {@code createInventory} overload caps the title at <strong>32 characters</strong>
 * before 1.20 (a longer title throws/garbles on the floor); 1.20+ removed the cap. So this truncates to 32
 * only when {@link Capabilities#atLeast} reports a pre-1.20 server, and never splits a trailing {@code §}
 * colour marker (which would leave a dangling control char). The limit counts the <em>translated</em> string
 * the client receives — section signs included — so colour codes are translated first, then measured.
 *
 * <p>This is the one place the 32-char title gap (absent in the original single menu, which had a short fixed
 * title) is handled, so every config-driven menu title is safe across 1.17.1 → 26.1.x.
 */
public final class MenuText {

    /** The legacy {@code String}-title length limit (pre-1.20). */
    static final int LEGACY_TITLE_LIMIT = 32;

    private MenuText() {
    }

    /** Translate {@code &} codes in {@code legacy} and truncate to the server's safe title length. */
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
