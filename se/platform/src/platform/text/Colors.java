package platform.text;

import compile.load.ChatColorRgb;

/**
 * Legacy {@code '&'}→{@code '§'} colour-code translation — the ONE home every module routes through
 * (ADR-0033; docs/architecture.md §2). No Bukkit, so rendered text is testable without a server; legacy
 * codes not Adventure is the codebase's floor-safe text stance. Only translates {@code '&'} before a valid
 * code char (0-9, a-f, k-o, r, hex x); a stray {@code '&'} is left untouched.
 *
 * <p>Hex colours (ADR-0062): {@code {#RRGGBB}} — also the {@code &{#RRGGBB}} and {@code &#RRGGBB} spellings —
 * translates to the installed {@link HexMode}'s runtime form: the 1.16+ {@code §x§R§R§G§G§B§B} escape on the
 * modern range, the nearest of the 16 legacy colours on the true-1.8 lane. A malformed token (bad length,
 * non-hex) stays literal text — the stray-{@code &} rule extended to braces.
 */
public final class Colors {

    private static final String CODES = "0123456789abcdefklmnorxABCDEFKLMNORX";
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    /** The runtime form a hex colour translates to — era-selected once at boot via {@code EraServices.hexMode()}. */
    public enum HexMode {
        /** The 1.16+ legacy-hex escape {@code §x§R§R§G§G§B§B} — correct on the whole modern range (floor 1.17.1). */
        MODERN {
            @Override String emit(int r, int g, int b) {
                StringBuilder out = new StringBuilder(14).append('§').append('x');
                pair(out, r);
                pair(out, g);
                pair(out, b);
                return out.toString();
            }
        },
        /** The true-1.8 degrade: the nearest of the 16 legacy colours by Euclidean RGB distance. */
        NEAREST_LEGACY {
            @Override String emit(int r, int g, int b) {
                return "§" + ChatColorRgb.nearestCode(r, g, b);
            }
        };

        abstract String emit(int r, int g, int b);

        private static void pair(StringBuilder out, int channel) {
            out.append('§').append(HEX[(channel >> 4) & 0xF]).append('§').append(HEX[channel & 0xF]);
        }
    }

    // Installed once from the composition root (BootCore — the ItemFactory.itemWrapWidth idiom); the MODERN
    // default is correct for the whole modern range and keeps unit tests deterministic with no install.
    private static volatile HexMode hexMode = HexMode.MODERN;

    private Colors() {
    }

    public static void hexMode(HexMode mode) {
        hexMode = mode == null ? HexMode.MODERN : mode;
    }

    public static String translate(String text) {
        return translate(text, hexMode);
    }

    /** {@link #translate(String)} with the hex form explicit — the pure seam the degrade tests drive. */
    public static String translate(String text, HexMode mode) {
        if (text == null || (text.indexOf('&') < 0 && text.indexOf('{') < 0
                && (mode != HexMode.NEAREST_LEGACY || text.indexOf('§') < 0))) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            int token = hexTokenSpan(text, i);
            if (token > 0) {
                emitHex(out, mode, hexTokenValue(text, i, token));
                i += token - 1;
                continue;
            }
            if (mode == HexMode.NEAREST_LEGACY) {
                // A modern-authored §x/&x escape run reaching the 1.8 lane degrades too, not just the token form.
                int escape = hexEscapeSpan(text, i);
                if (escape > 0) {
                    emitHex(out, mode, hexEscapeValue(text, i));
                    i += escape - 1;
                    continue;
                }
            }
            char c = text.charAt(i);
            if (c == '&' && i + 1 < text.length() && CODES.indexOf(text.charAt(i + 1)) >= 0) {
                out.append('§');
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    /**
     * The length of the hex-colour construct starting at {@code i} — a {@code {#RRGGBB}} token (9), its
     * {@code &{#RRGGBB}} (10) / {@code &#RRGGBB} (8) spellings, or a {@code §x}/{@code &x} six-pair escape
     * run (14) — else {@code 0}. Public so visible-width measurement ({@code TextWrap}) skips exactly what
     * {@link #translate} treats as a colour.
     */
    public static int hexSpan(String s, int i) {
        int token = hexTokenSpan(s, i);
        return token > 0 ? token : hexEscapeSpan(s, i);
    }

    private static int hexTokenSpan(String s, int i) {
        char c = s.charAt(i);
        if (c == '{') {
            return braceSpan(s, i);
        }
        if (c != '&' || i + 1 >= s.length()) {
            return 0;
        }
        if (s.charAt(i + 1) == '{') {
            int brace = braceSpan(s, i + 1);
            return brace > 0 ? brace + 1 : 0;
        }
        return s.charAt(i + 1) == '#' && hexDigits(s, i + 2) ? 8 : 0;
    }

    /** {@code {#RRGGBB}} at {@code i} → 9, else 0. */
    private static int braceSpan(String s, int i) {
        return i + 8 < s.length() && s.charAt(i + 1) == '#' && hexDigits(s, i + 2) && s.charAt(i + 8) == '}' ? 9 : 0;
    }

    /** {@code §x}/{@code &x} then six marker+hex-digit pairs at {@code i} → 14, else 0. */
    private static int hexEscapeSpan(String s, int i) {
        char m = s.charAt(i);
        if ((m != '&' && m != '§') || i + 13 >= s.length() || (s.charAt(i + 1) != 'x' && s.charAt(i + 1) != 'X')) {
            return 0;
        }
        for (int p = i + 2; p < i + 14; p += 2) {
            char marker = s.charAt(p);
            if ((marker != '&' && marker != '§') || Character.digit(s.charAt(p + 1), 16) < 0) {
                return 0;
            }
        }
        return 14;
    }

    private static boolean hexDigits(String s, int from) {
        if (from + 6 > s.length()) {
            return false;
        }
        for (int k = from; k < from + 6; k++) {
            if (Character.digit(s.charAt(k), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    private static void emitHex(StringBuilder out, HexMode mode, int rgb) {
        out.append(mode.emit((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF));
    }

    private static int hexTokenValue(String s, int i, int span) {
        int from = span == 10 ? i + 3 : i + 2; // past "&{#" (10) or "{#"/"&#" (9/8)
        return Integer.parseInt(s.substring(from, from + 6), 16);
    }

    private static int hexEscapeValue(String s, int i) {
        StringBuilder hex = new StringBuilder(6);
        for (int p = i + 3; p < i + 14; p += 2) {
            hex.append(s.charAt(p));
        }
        return Integer.parseInt(hex.toString(), 16);
    }
}
