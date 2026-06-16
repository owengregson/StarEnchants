package schema.grammar;

import java.util.ArrayList;
import java.util.List;

/**
 * The low-level lexing primitive: split a line on a delimiter at the <em>top
 * level only</em>, ignoring delimiters nested inside brackets or quotes.
 *
 * <p>An effect line is colon-separated ({@code HEAD:arg:arg}), but arguments may
 * themselves contain colons inside an inline tag ({@code <random 1-3>}), a
 * selector body ({@code @Sel{r=5}}), or a quoted string ({@code "a:b"}). A naive
 * {@code String.split(":")} would shred those; this lexer respects the nesting of
 * {@code () [] {} <>} and single/double quotes (with backslash escapes), and
 * tracks the 1-based column of every segment for diagnostics
 * (docs/architecture.md §2, se-schema/grammar).
 */
public final class Lexer {

    private Lexer() {
    }

    /**
     * Split {@code input} on {@code delim}, but only where bracket depth is zero
     * and not inside a quote. Always returns at least one token (possibly empty);
     * consecutive delimiters yield empty segments, preserving positional meaning.
     */
    public static List<Tok> splitTop(String input, char delim) {
        List<Tok> out = new ArrayList<>();
        int round = 0, square = 0, curly = 0, angle = 0;
        char quote = 0;
        StringBuilder cur = new StringBuilder();
        int segStartCol = 1; // 1-based column of the current segment's first char

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (quote != 0) {
                cur.append(c);
                if (c == '\\' && i + 1 < input.length()) {
                    cur.append(input.charAt(++i)); // escaped char taken literally
                } else if (c == quote) {
                    quote = 0;
                }
                continue;
            }

            switch (c) {
                case '\'', '"' -> {
                    quote = c;
                    cur.append(c);
                }
                case '(' -> { round++; cur.append(c); }
                case ')' -> { if (round > 0) round--; cur.append(c); }
                case '[' -> { square++; cur.append(c); }
                case ']' -> { if (square > 0) square--; cur.append(c); }
                case '{' -> { curly++; cur.append(c); }
                case '}' -> { if (curly > 0) curly--; cur.append(c); }
                case '<' -> { angle++; cur.append(c); }
                case '>' -> { if (angle > 0) angle--; cur.append(c); }
                default -> {
                    if (c == delim && round == 0 && square == 0 && curly == 0 && angle == 0) {
                        out.add(new Tok(cur.toString(), segStartCol));
                        cur.setLength(0);
                        segStartCol = i + 2; // next char's 1-based column
                    } else {
                        cur.append(c);
                    }
                }
            }
        }
        out.add(new Tok(cur.toString(), segStartCol));
        return out;
    }
}
