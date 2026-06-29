package item.render;

import item.mint.ItemFactory;
import java.util.regex.Pattern;

/**
 * The transmog enchant-count name suffix — a bracketed {@code [N]} appended to an item's display NAME that
 * reflects how many CUSTOM enchants sit on it (vanilla Minecraft enchants never count). It behaves as a
 * fixed part of the name once any custom enchant is present: re-stamped on every enchant-set change and
 * re-appended across a rename, and removed entirely the moment the custom-enchant count drops to zero.
 *
 * <p>Single source for the strip + (re)append so every mutation/rename path stays consistent (combat
 * {@link LoreRenderer#apply} stamps it from state; the nametag rename re-appends it from the typed name).
 * Pure string logic — {@link ItemFactory#color} is plain {@code &}-code translation, so this is unit-testable
 * with no server. The template is the configured suffix (default {@code &r &d[&b&l&n{COUNT}&r&d]}); its
 * {@link #COUNT_PLACEHOLDER} marks the count slot.
 */
public final class EnchantCountSuffix {

    /** The placeholder the configured suffix template substitutes the count into. */
    public static final String COUNT_PLACEHOLDER = "{COUNT}";

    /** Marks the count slot while locating it in the translated template; never appears in a real name. */
    private static final String SENTINEL = "\u0000";

    private EnchantCountSuffix() {
    }

    /**
     * The display name {@code currentName} should carry for {@code count} custom enchants: strip any
     * previously-applied suffix, then append a fresh one IFF {@code count > 0}. A {@code count} of {@code 0}
     * (no custom enchants) yields the bare base name with no suffix. A {@code null}/blank {@code template}
     * leaves the name untouched (suffix feature disabled). Returns {@code ""} when the result is empty
     * (no base name and no suffix) — callers clear the display name in that case.
     */
    public static String nameFor(String currentName, String template, int count) {
        if (template == null || template.isBlank()) {
            return currentName == null ? "" : currentName;
        }
        String base = currentName == null ? "" : strip(currentName, template);
        if (count <= 0) {
            return base;
        }
        return base + ItemFactory.color(template.replace(COUNT_PLACEHOLDER, Integer.toString(count)));
    }

    /**
     * Strip a previously-applied count suffix from {@code name} (so a re-stamp REPLACES it). Builds a regex
     * from the translated suffix template with the count region as {@code \d+}, anchored to the end; a
     * template with no placeholder strips nothing. The colour-code runs match TOLERANTLY
     * ({@link #tolerantColourCodes}) so a suffix strips itself even after Bukkit's {@code ItemMeta} normalises
     * its codes on a round-trip ({@code §r§d -> §d}) — otherwise the old suffix survives and a re-stamp STACKS
     * it ({@code [2] [2]}).
     */
    public static String strip(String name, String template) {
        if (name == null || template == null) {
            return name;
        }
        String translated = ItemFactory.color(template.replace(COUNT_PLACEHOLDER, SENTINEL));
        int idx = translated.indexOf(SENTINEL);
        if (idx < 0) {
            return name;
        }
        String regex = tolerantColourCodes(translated.substring(0, idx))
                + "\\d+" + tolerantColourCodes(translated.substring(idx + SENTINEL.length())) + "$";
        return name.replaceAll(regex, "");
    }

    /**
     * A regex source matching {@code text} regardless of how Bukkit normalises its colour codes on a meta
     * round-trip: every maximal run of {@code §}-codes becomes a tolerant {@code (?:§.)*}; the literal runs
     * (brackets, spaces) stay {@link Pattern#quote quoted} as the structural anchors.
     */
    private static String tolerantColourCodes(String text) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        int n = text.length();
        while (i < n) {
            if (text.charAt(i) == '§' && i + 1 < n) {
                while (i + 1 < n && text.charAt(i) == '§') {
                    i += 2; // consume a maximal run of §-code pairs
                }
                out.append("(?:§.)*");
            } else {
                int start = i;
                while (i < n && !(text.charAt(i) == '§' && i + 1 < n)) {
                    i++;
                }
                out.append(Pattern.quote(text.substring(start, i)));
            }
        }
        return out.toString();
    }
}
