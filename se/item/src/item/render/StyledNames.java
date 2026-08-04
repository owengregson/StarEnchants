package item.render;

import java.util.List;
import java.util.function.Function;
import platform.text.Tokens;

/**
 * How several component identities read as ONE token — shared by the multi-crystal {@code {CRYSTAL}} token
 * (ADR-0034 §1) and the composite-mask {@code {NAME}} one (ADR-0074). The names are comma-joined so a fold reads
 * each component in its OWN colour (e.g. {@code &4&lChaos&6&l, &e&lLight}); a single component is just itself,
 * so nothing rendered before either family learned to combine changes.
 *
 * <p>The join separator is the template's LEADING format run + {@code ", "} — a template opening
 * {@code &6&lArmor Crystal (} separates with {@code &6&l, }, resetting each gap to the template's base colour
 * before the next name supplies its own; a template opening with plain text separates with a bare {@code ", "}.
 * This keeps the colour bookkeeping out of the author's hands: they style each component's {@code display} and
 * nothing else.
 */
public final class StyledNames {

    private StyledNames() {
    }

    /** Render {@code template}'s {@code token} from the styled display names of {@code keys}. */
    public static String render(String template, String token, List<String> keys,
                                Function<String, String> displayNameOf) {
        return Tokens.sub(template, token, join(template, keys, displayNameOf));
    }

    /** The comma-joined styled display names for {@code keys} (an unknown key falls back to the key itself). */
    public static String join(String template, List<String> keys, Function<String, String> displayNameOf) {
        String separator = leadingCodes(template) + ", ";
        StringBuilder out = new StringBuilder();
        for (String key : keys) {
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
