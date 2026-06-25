package schema.grammar;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a line on a delimiter at the top level only, respecting nesting of
 * {@code () [] {} <>} and quotes (backslash escapes) so colons inside inline tags,
 * selector bodies, or quoted strings survive a naive split. Tracks 1-based columns
 * for diagnostics (docs/architecture.md §2).
 */
public final class Lexer {

    private Lexer() {
    }

    /** Always returns >=1 token; consecutive delimiters yield empty segments, preserving position. */
    public static List<Tok> splitTop(String input, char delim) {
        List<Tok> out = new ArrayList<>();
        int round = 0, square = 0, curly = 0, angle = 0;
        char quote = 0;
        StringBuilder cur = new StringBuilder();
        int segStartCol = 1;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (quote != 0) {
                cur.append(c);
                if (c == '\\' && i + 1 < input.length()) {
                    cur.append(input.charAt(++i));
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
