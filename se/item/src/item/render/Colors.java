package item.render;

/**
 * Legacy {@code '&'}→{@code '§'} colour-code translation (§4.2). No Bukkit, so rendered text is testable
 * without a server; legacy codes not Adventure is the codebase's floor-safe text stance (mirrors
 * {@code DispatchSink}). Only translates {@code '&'} before a valid code char (0-9, a-f, k-o, r, hex x);
 * a stray {@code '&'} is left untouched.
 */
public final class Colors {

    private static final String CODES = "0123456789abcdefklmnorxABCDEFKLMNORX";

    private Colors() {
    }

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
