package item.render;

/**
 * Legacy colour-code translation (docs/architecture.md §4.2) — the {@code '&'} alternate codes
 * authors write become the section sign ({@code '§'}) the client renders. Pure string work and
 * no Bukkit, so the renderer's output is final display text testable without a server (the
 * codebase's floor-safe text stance — legacy codes, not Adventure, mirrors {@code DispatchSink}).
 *
 * <p>An {@code '&'} only translates when followed by a valid code character (0-9, a-f, k-o, r, and
 * the hex sigil x); a stray {@code '&'} in prose is left untouched.
 */
public final class Colors {

    private static final String CODES = "0123456789abcdefklmnorxABCDEFKLMNORX";

    private Colors() {
    }

    /** Translate {@code '&'} alternate colour codes to {@code '§'}; a lone {@code '&'} is left as-is. */
    public static String translate(String text) {
        if (text == null || text.indexOf('&') < 0) {
            return text;
        }
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length - 1; i++) {
            if (chars[i] == '&' && CODES.indexOf(chars[i + 1]) >= 0) {
                chars[i] = '§';
            }
        }
        return new String(chars);
    }
}
