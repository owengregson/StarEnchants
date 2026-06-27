package item.render;

import java.util.ArrayList;
import java.util.List;

/**
 * Word-wraps legacy {@code '&'}-coded text to a visible width (§4.2) — colour codes ({@code &a}, {@code &l},
 * {@code §a}, …) do NOT count toward the width, and the active colour/format carries onto each continuation
 * line so a wrapped line keeps its colour. Embedded {@code '\n'} are honoured as hard breaks (and preserved
 * as blank lines). A whole-word longer than the width is never split (standard word-wrap). Pure: no Bukkit,
 * so it is unit-testable without a server.
 */
public final class TextWrap {

    /** Legacy code chars (colour 0-9 a-f, format k-o, reset r); hex {@code &x} is not specially handled. */
    private static final String CODES = "0123456789abcdefklmnorABCDEFKLMNOR";

    private TextWrap() {
    }

    /**
     * Wrap {@code text} so each output line is at most {@code charsPerLine} VISIBLE chars. A {@code null}/empty
     * text yields no lines; a {@code charsPerLine <= 0} disables wrapping (each {@code '\n'} segment becomes
     * one line verbatim).
     */
    public static List<String> wrap(String text, int charsPerLine) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String segment : text.split("\n", -1)) {
            wrapSegment(segment, charsPerLine, out);
        }
        return out;
    }

    private static void wrapSegment(String segment, int width, List<String> out) {
        if (width <= 0 || visibleLength(segment) <= width) {
            out.add(segment);
            return;
        }
        String carried = "";
        StringBuilder line = new StringBuilder();
        int lineVisible = 0;
        for (String word : segment.split(" ", -1)) {
            int wordVisible = visibleLength(word);
            int sep = line.length() == carried.length() ? 0 : 1; // a space unless the line is only carried codes
            if (lineVisible + sep + wordVisible > width && lineVisible > 0) {
                out.add(line.toString());
                carried = lastColors(line.toString());     // the active colour/format at the wrap point
                line = new StringBuilder(carried);
                lineVisible = 0;
                sep = 0;
            }
            if (sep == 1) {
                line.append(' ');
                lineVisible++;
            }
            line.append(word);
            lineVisible += wordVisible;
        }
        out.add(line.toString());
    }

    /** The number of visible characters in {@code s} (colour codes excluded). */
    public static int visibleLength(String s) {
        int length = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < s.length() && CODES.indexOf(s.charAt(i + 1)) >= 0) {
                i++; // skip the code char — it is invisible
            } else {
                length++;
            }
        }
        return length;
    }

    /**
     * The trailing active colour/format of {@code input}, in {@code '&'} form, to re-emit at the start of a
     * continuation line — Bukkit {@code getLastColors} semantics: a colour or reset clears prior formatting,
     * format codes (k-o) accumulate.
     */
    static String lastColors(String input) {
        StringBuilder result = new StringBuilder();
        for (int index = input.length() - 2; index >= 0; index--) {
            char marker = input.charAt(index);
            if (marker != '&' && marker != '§') {
                continue;
            }
            char code = Character.toLowerCase(input.charAt(index + 1));
            if (CODES.indexOf(code) < 0) {
                continue;
            }
            result.insert(0, "&" + code);
            boolean colourOrReset = (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f') || code == 'r';
            if (colourOrReset) {
                break; // a colour/reset resets everything before it — nothing earlier carries
            }
        }
        return result.toString();
    }
}
