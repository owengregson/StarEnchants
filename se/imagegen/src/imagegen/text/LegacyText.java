package imagegen.text;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a legacy {@code §}/{@code &}-coded line into {@link StyledRun}s the way Minecraft lays out item lore: a
 * colour code (or {@code §r}) resets all formatting, format codes ({@code §l §o §n §m §k}) accumulate, and the
 * 1.16 hex form {@code §x§R§R§G§G§B§B} sets an explicit colour. Either {@code §} or {@code &} is accepted as the
 * marker so fixtures can be authored with the friendlier {@code &}.
 */
public final class LegacyText {

    private LegacyText() {
    }

    public static List<StyledRun> parse(String text, int defaultRgb) {
        List<StyledRun> runs = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return runs;
        }
        char[] s = text.toCharArray();
        StringBuilder buf = new StringBuilder();
        int color = defaultRgb;
        boolean bold = false, italic = false, under = false, strike = false, obf = false;

        for (int i = 0; i < s.length; i++) {
            char ch = s[i];
            boolean marker = (ch == '§' || ch == '&') && i + 1 < s.length;
            if (!marker) {
                buf.append(ch);
                continue;
            }
            char code = Character.toLowerCase(s[i + 1]);

            // Hex colour: §x then six §R pairs. Anything malformed falls through to a literal.
            if (code == 'x' && i + 13 < s.length) {
                String hex = "" + s[i + 3] + s[i + 5] + s[i + 7] + s[i + 9] + s[i + 11] + s[i + 13];
                if (isHex(hex)) {
                    flush(runs, buf, color, bold, italic, under, strike, obf);
                    color = Integer.parseInt(hex, 16);
                    bold = italic = under = strike = obf = false;
                    i += 13;
                    continue;
                }
            }

            int c = McColors.rgb(code);
            if (c >= 0) { // a colour resets formatting
                flush(runs, buf, color, bold, italic, under, strike, obf);
                color = c;
                bold = italic = under = strike = obf = false;
                i++;
                continue;
            }
            switch (code) {
                case 'l' -> {
                    flush(runs, buf, color, bold, italic, under, strike, obf);
                    bold = true;
                    i++;
                }
                case 'o' -> {
                    flush(runs, buf, color, bold, italic, under, strike, obf);
                    italic = true;
                    i++;
                }
                case 'n' -> {
                    flush(runs, buf, color, bold, italic, under, strike, obf);
                    under = true;
                    i++;
                }
                case 'm' -> {
                    flush(runs, buf, color, bold, italic, under, strike, obf);
                    strike = true;
                    i++;
                }
                case 'k' -> {
                    flush(runs, buf, color, bold, italic, under, strike, obf);
                    obf = true;
                    i++;
                }
                case 'r' -> {
                    flush(runs, buf, color, bold, italic, under, strike, obf);
                    color = defaultRgb;
                    bold = italic = under = strike = obf = false;
                    i++;
                }
                default -> buf.append(ch); // a stray marker — keep it literally
            }
        }
        flush(runs, buf, color, bold, italic, under, strike, obf);
        return runs;
    }

    private static void flush(List<StyledRun> runs, StringBuilder buf, int color, boolean bold, boolean italic,
            boolean under, boolean strike, boolean obf) {
        if (buf.length() == 0) {
            return;
        }
        runs.add(new StyledRun(buf.toString(), color, bold, italic, under, strike, obf));
        buf.setLength(0);
    }

    private static boolean isHex(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.digit(s.charAt(i), 16) < 0) {
                return false;
            }
        }
        return true;
    }
}
