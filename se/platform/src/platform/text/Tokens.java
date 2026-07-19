package platform.text;

import java.util.ArrayList;
import java.util.List;

/** {@code {TOKEN}} substitution for item/message likeness templates ({@code {SUCCESS}}, {@code {KINDS}},
 *  {@code {TIER_COLOR}}, ...). Canonical token spelling is UNDERSCORE; for any key containing {@code '_'} the
 *  hyphen spelling is accepted as an alias ({@code {TIER-COLOR}} → {@code {TIER_COLOR}}) so pre-existing user
 *  configs keep rendering. NOT the lang.yml engine — platform.lang.Messages/compile.load.Lang keep their own
 *  catalogue substitution. */
public final class Tokens {
    private Tokens() { }

    /** Replace each pair's {@code {KEY}} (and its {@code {K-E-Y}} hyphen alias when KEY contains {@code '_'})
     *  with {@code String.valueOf(value)}. */
    public static String sub(String line, Object... kv) {
        if (line == null || line.indexOf('{') < 0 || kv.length == 0) return line;
        String out = line;
        for (int i = 0; i + 1 < kv.length; i += 2) {
            String key = String.valueOf(kv[i]);
            String value = String.valueOf(kv[i + 1]);
            out = out.replace("{" + key + "}", value);
            if (key.indexOf('_') >= 0) out = out.replace("{" + key.replace('_', '-') + "}", value);
        }
        return out;
    }

    /** {@link #sub} applied per line. */
    public static List<String> subLines(List<String> lines, Object... kv) {
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) out.add(sub(line, kv));
        return out;
    }

    /** {@link #subLines} with ONE line-expanding token: a template line containing {@code {expandKey}} emits one
     *  copy per element of {@code expansion} ({@code {expandKey}} → element, the other tokens filled on each
     *  copy); an EMPTY expansion drops that template line (the enchant-book {@code {DESCRIPTION}} rule, §I). */
    public static List<String> expandLines(List<String> lines, String expandKey, List<String> expansion, Object... kv) {
        String token = "{" + expandKey + "}";
        List<String> out = new ArrayList<>(lines.size() + expansion.size());
        for (String line : lines) {
            if (line.contains(token)) {
                for (String piece : expansion) out.add(sub(line.replace(token, piece), kv));
            } else {
                out.add(sub(line, kv));
            }
        }
        return out;
    }

    /**
     * Template tolerance for a colour token (ADR-0052, promoted for reforges): pack specs write
     * {@code &{COLOR}} (the colour as a bare code) while the substituted value carries its own
     * ampersands ({@code "&7"} / {@code "{#RRGGBB}"}), which would render a literal {@code &}. Both
     * authored forms mean the same thing, so {@code &{COLOR}} folds to {@code {COLOR}} before
     * substitution — never a per-render policy an operator has to know about.
     */
    public static String colorTolerant(String template) {
        return template.replace("&{COLOR}", "{COLOR}");
    }
}
