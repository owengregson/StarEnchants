package feature.menu;

import compile.load.TierRegistry;
import item.render.Descriptions;
import java.util.List;
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

    /** A tier's legacy colour code (e.g. {@code &e}), or grey ({@code &7}) when the tier is null/unregistered. */
    public static String tierColor(TierRegistry tiers, String tier) {
        if (tier == null) {
            return "&7";
        }
        TierRegistry.Tier t = tiers.tier(tier);
        return t != null && !t.color().isBlank() ? t.color() : "&7";
    }

    /**
     * An enchant name styled by the enchant-book {@code name:} template, so a menu icon's name matches the
     * unapplied book (single source of truth). Fills the template's {@code {TIER_COLOR}} / {@code {ENCHANT}} /
     * {@code {LEVEL}} placeholders; a blank {@code level} drops the {@code " {LEVEL}"} slot (a level-less icon).
     * The returned string still carries {@code '&'} codes — the caller's {@code ItemFactory.build} translates.
     */
    public static String enchantName(String template, String tierColor, String display, String level) {
        String tc = tierColor == null ? "" : tierColor;
        String lvl = level == null ? "" : level;
        String out = template
                .replace("{TIER_COLOR}", tc)
                .replace("{TIER-COLOR}", tc)
                .replace("{ENCHANT}", display);
        return lvl.isBlank()
                ? out.replace(" {LEVEL}", "").replace("{LEVEL}", "")
                : out.replace("{LEVEL}", lvl);
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
