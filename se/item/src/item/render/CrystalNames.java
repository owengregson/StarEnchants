package item.render;

import java.util.List;
import java.util.function.Function;

/**
 * Single source of truth for a crystal item's NAME string (ADR-0034 §1/§5). The {@code {CRYSTAL}} token expands
 * to the component crystals' STYLED display names, comma-joined so a merge reads each name in its own colour
 * (e.g. {@code &4&lChaos&6&l, &e&lLight}). Shared by the mint path (the physical item name) and the gear
 * renderer (the on-item line), so the two never diverge.
 *
 * <p>The join separator is the name template's LEADING format run + {@code ", "} — a template opening
 * {@code &6&lArmor Crystal (} separates names with {@code &6&l, }, resetting each gap to the template's base
 * colour before the next name supplies its own; a template opening with plain text separates with a bare
 * {@code ", "}. This keeps the colour bookkeeping out of the author's hands: they just style each crystal's
 * {@code display}.
 */
public final class CrystalNames {

    private CrystalNames() {
    }

    /** Render {@code template}'s {@code {CRYSTAL}} token from {@code componentKeys} (single or merged). */
    public static String render(String template, List<String> componentKeys, Function<String, String> displayNameOf) {
        return template.replace("{CRYSTAL}", join(template, componentKeys, displayNameOf));
    }

    /** The comma-joined styled display names for {@code componentKeys} (an unknown key falls back to the key). */
    public static String join(String template, List<String> componentKeys, Function<String, String> displayNameOf) {
        String separator = leadingCodes(template) + ", ";
        StringBuilder out = new StringBuilder();
        for (String key : componentKeys) {
            if (out.length() > 0) {
                out.append(separator);
            }
            String display = displayNameOf.apply(key);
            out.append(display != null ? display : key);
        }
        return out.toString();
    }

    /** The run of leading {@code &x} colour/format codes at the very start of {@code template} (e.g. {@code &6&l}). */
    static String leadingCodes(String template) {
        int i = 0;
        int n = template.length();
        while (i + 1 < n && template.charAt(i) == '&' && isCode(template.charAt(i + 1))) {
            i += 2;
        }
        return template.substring(0, i);
    }

    private static boolean isCode(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')
                || (c >= 'k' && c <= 'o') || (c >= 'K' && c <= 'O') || c == 'r' || c == 'R';
    }
}
