package schema.grammar.expr;

import schema.diag.DiagCode;
import schema.diag.Diagnostics;
import schema.diag.Source;
import java.util.ArrayList;
import java.util.List;

/**
 * Tokenizer for the condition-expression sublanguage (docs/architecture.md §3.4). Never throws:
 * lexical faults become {@code E_PARSE} diagnostics and recover best-effort, so the parser always
 * gets a usable, EOF-terminated stream. Columns are 1-based within the expression text.
 */
public final class ExprLexer {

    private final String src;
    private final Source lineSource;
    private final Diagnostics diags;
    private int pos;

    private ExprLexer(String src, Source lineSource, Diagnostics diags) {
        this.src = src;
        this.lineSource = lineSource;
        this.diags = diags;
    }

    /** The returned list always ends with a single {@link ExprTok.Kind#EOF} just past the input. */
    public static List<ExprTok> tokenize(String text, Source lineSource, Diagnostics diags) {
        return new ExprLexer(text, lineSource, diags).run();
    }

    private List<ExprTok> run() {
        List<ExprTok> out = new ArrayList<>();
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (Character.isWhitespace(c)) {
                pos++;
                continue;
            }
            int startCol = col(pos);
            switch (c) {
                case '&' -> out.add(twoCharOp('&', ExprTok.Kind.AND, "&&", startCol));
                case '|' -> out.add(twoCharOp('|', ExprTok.Kind.OR, "||", startCol));
                case '(' -> { out.add(new ExprTok(ExprTok.Kind.LPAREN, "(", startCol)); pos++; }
                case ')' -> { out.add(new ExprTok(ExprTok.Kind.RPAREN, ")", startCol)); pos++; }
                case ',' -> { out.add(new ExprTok(ExprTok.Kind.COMMA, ",", startCol)); pos++; }
                case '=' -> out.add(twoCharOp('=', ExprTok.Kind.EQ, "==", startCol));
                case '!' -> out.add(bangOrNe(startCol));
                case '<' -> out.add(relational('<', ExprTok.Kind.LT, ExprTok.Kind.LE, startCol));
                case '>' -> out.add(relational('>', ExprTok.Kind.GT, ExprTok.Kind.GE, startCol));
                case '%' -> out.add(variable(startCol));
                case ':' -> { out.add(new ExprTok(ExprTok.Kind.COLON, ":", startCol)); pos++; }
                case '+' -> { out.add(new ExprTok(ExprTok.Kind.PLUS, "+", startCol)); pos++; }
                case '-' -> { out.add(new ExprTok(ExprTok.Kind.MINUS, "-", startCol)); pos++; }
                case '*' -> { out.add(new ExprTok(ExprTok.Kind.STAR, "*", startCol)); pos++; }
                case '/' -> { out.add(new ExprTok(ExprTok.Kind.SLASH, "/", startCol)); pos++; }
                case '"', '\'' -> out.add(string(c, startCol));
                default -> {
                    if (isNumberStart(c)) {
                        out.add(number(startCol));
                    } else if (isIdentStart(c)) {
                        out.add(ident(startCol));
                    } else {
                        diags.error(DiagCode.E_PARSE_BAD_CHAR, "unexpected character '" + c + "'",
                                lineSource.atColumn(startCol),
                                "the condition language allows numbers, %variables%, "
                                        + "\"strings\", booleans, && || ! ( ) , and comparators");
                        pos++;
                    }
                }
            }
        }
        out.add(new ExprTok(ExprTok.Kind.EOF, "", col(src.length())));
        return out;
    }

    /** A doubled operator ({@code && || ==}); a missing second char is reported but the token still emits. */
    private ExprTok twoCharOp(char second, ExprTok.Kind kind, String lexeme, int startCol) {
        char first = src.charAt(pos);
        pos++;
        if (pos < src.length() && src.charAt(pos) == second) {
            pos++;
        } else {
            diags.error(DiagCode.E_PARSE_HALF_OP, "expected '" + lexeme + "' but found a single '" + first + "'",
                    lineSource.atColumn(startCol), "did you mean '" + lexeme + "'?");
        }
        return new ExprTok(kind, lexeme, startCol);
    }

    private ExprTok bangOrNe(int startCol) {
        pos++;
        if (pos < src.length() && src.charAt(pos) == '=') {
            pos++;
            return new ExprTok(ExprTok.Kind.NE, "!=", startCol);
        }
        return new ExprTok(ExprTok.Kind.BANG, "!", startCol);
    }

    private ExprTok relational(char base, ExprTok.Kind bare, ExprTok.Kind orEqual, int startCol) {
        pos++;
        if (pos < src.length() && src.charAt(pos) == '=') {
            pos++;
            return new ExprTok(orEqual, base + "=", startCol);
        }
        return new ExprTok(bare, String.valueOf(base), startCol);
    }

    /** A {@code %…%} variable; an empty or unterminated body is reported but still yields a VAR token. */
    private ExprTok variable(int startCol) {
        pos++;
        int bodyStart = pos;
        while (pos < src.length() && src.charAt(pos) != '%') {
            pos++;
        }
        String body = src.substring(bodyStart, pos);
        if (pos >= src.length()) {
            diags.error(DiagCode.E_PARSE_UNTERMINATED, "unterminated variable (missing closing '%')",
                    lineSource.atColumn(startCol), "close it with a '%', e.g. %victim.health%");
        } else {
            pos++;
        }
        if (body.isEmpty()) {
            diags.error(DiagCode.E_PARSE_EMPTY_VAR, "empty variable '%%'",
                    lineSource.atColumn(startCol), "name the variable, e.g. %victim.health%");
        }
        return new ExprTok(ExprTok.Kind.VAR, body, startCol);
    }

    /** A quoted string with {@code \} escapes; token text is the unescaped contents (no quotes). */
    private ExprTok string(char quote, int startCol) {
        pos++;
        StringBuilder sb = new StringBuilder();
        boolean closed = false;
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == '\\' && pos + 1 < src.length()) {
                sb.append(src.charAt(pos + 1));
                pos += 2;
            } else if (c == quote) {
                pos++;
                closed = true;
                break;
            } else {
                sb.append(c);
                pos++;
            }
        }
        if (!closed) {
            diags.error(DiagCode.E_PARSE_UNTERMINATED, "unterminated string literal",
                    lineSource.atColumn(startCol),
                    "close it with a matching " + quote + " quote");
        }
        return new ExprTok(ExprTok.Kind.STRING, sb.toString(), startCol);
    }

    /** Digits with an optional single decimal point. */
    private ExprTok number(int startCol) {
        int start = pos;
        boolean dot = false;
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == '.' && !dot) {
                dot = true;
                pos++;
            } else if (Character.isDigit(c)) {
                pos++;
            } else {
                break;
            }
        }
        return new ExprTok(ExprTok.Kind.NUMBER, src.substring(start, pos), startCol);
    }

    /** A bare identifier; covers {@code true}/{@code false} and enum-ish operands. */
    private ExprTok ident(int startCol) {
        int start = pos;
        pos++;
        while (pos < src.length() && isIdentPart(src.charAt(pos))) {
            pos++;
        }
        String text = src.substring(start, pos);
        // contains/matchesregex are case-insensitive reserved words, tokenized as operators not identifiers.
        if (text.equalsIgnoreCase("contains")) {
            return new ExprTok(ExprTok.Kind.CONTAINS, text, startCol);
        }
        if (text.equalsIgnoreCase("matchesregex")) {
            return new ExprTok(ExprTok.Kind.MATCHES_REGEX, text, startCol);
        }
        return new ExprTok(ExprTok.Kind.IDENT, text, startCol);
    }

    private static boolean isNumberStart(char c) {
        return Character.isDigit(c);
    }

    private static boolean isIdentStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isIdentPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.';
    }

    private int col(int idx) {
        return idx + 1;
    }
}
