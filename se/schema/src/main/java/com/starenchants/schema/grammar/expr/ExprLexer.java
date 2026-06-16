package com.starenchants.schema.grammar.expr;

import com.starenchants.schema.diag.Diagnostics;
import com.starenchants.schema.diag.Source;
import java.util.ArrayList;
import java.util.List;

/**
 * The tokenizer for the condition-expression sublanguage (docs/architecture.md §2,
 * §3.4).
 *
 * <p>Unlike the effect-line {@link com.starenchants.schema.grammar.Lexer} (which
 * splits a line on {@code :} respecting bracket/quote nesting), this lexer reads
 * the <em>expression</em> grammar: numbers, identifiers ({@code true}/{@code false}
 * and bare enum-ish words), {@code %scope.name%} variables, single- or
 * double-quoted strings (with {@code \} escapes), and the operators
 * {@code && || ! ( ) , < <= > >= == !=}. Whitespace separates tokens and is
 * otherwise discarded.
 *
 * <p>It never throws. Lexical faults (an unterminated string or {@code %…%}, a
 * stray character) are reported into the supplied {@link Diagnostics} with an
 * {@code E_PARSE} code at a precise {@link Source}, and the lexer recovers by
 * taking the construct best-effort or skipping the offending character, so the
 * parser still receives a usable, EOF-terminated token stream.
 *
 * <p>Columns are 1-based relative to the start of the expression text the lexer was
 * given; {@link Source#atColumn(int)} turns a token column into a diagnostic
 * position on the owning line.
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

    /**
     * Tokenize {@code text}. The returned list always ends with a single
     * {@link ExprTok.Kind#EOF} token positioned just past the input.
     *
     * @param text       the raw expression text (one line, no surrounding YAML)
     * @param lineSource the source of the line the expression sits on; token
     *                   columns are applied to it via {@link Source#atColumn(int)}
     * @param diags      collector for lexical {@code E_PARSE} diagnostics
     */
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
                case '"', '\'' -> out.add(string(c, startCol));
                default -> {
                    if (isNumberStart(c)) {
                        out.add(number(startCol));
                    } else if (isIdentStart(c)) {
                        out.add(ident(startCol));
                    } else {
                        diags.error("E_PARSE", "unexpected character '" + c + "'",
                                lineSource.atColumn(startCol),
                                "the condition language allows numbers, %variables%, "
                                        + "\"strings\", booleans, && || ! ( ) , and comparators");
                        pos++; // skip and recover
                    }
                }
            }
        }
        out.add(new ExprTok(ExprTok.Kind.EOF, "", col(src.length())));
        return out;
    }

    /**
     * A doubled operator like {@code &&} or {@code ||} or {@code ==}. If the second
     * char is missing, report it and still emit the intended token so the parser
     * can carry on (lone {@code &}/{@code |}/{@code =} are never valid alone here).
     */
    private ExprTok twoCharOp(char second, ExprTok.Kind kind, String lexeme, int startCol) {
        char first = src.charAt(pos);
        pos++;
        if (pos < src.length() && src.charAt(pos) == second) {
            pos++;
        } else {
            diags.error("E_PARSE", "expected '" + lexeme + "' but found a single '" + first + "'",
                    lineSource.atColumn(startCol), "did you mean '" + lexeme + "'?");
        }
        return new ExprTok(kind, lexeme, startCol);
    }

    /** {@code !=} (inequality) or a bare {@code !} (logical not). */
    private ExprTok bangOrNe(int startCol) {
        pos++; // consume '!'
        if (pos < src.length() && src.charAt(pos) == '=') {
            pos++;
            return new ExprTok(ExprTok.Kind.NE, "!=", startCol);
        }
        return new ExprTok(ExprTok.Kind.BANG, "!", startCol);
    }

    /** A relational operator: {@code <}/{@code <=} or {@code >}/{@code >=}. */
    private ExprTok relational(char base, ExprTok.Kind bare, ExprTok.Kind orEqual, int startCol) {
        pos++; // consume '<' or '>'
        if (pos < src.length() && src.charAt(pos) == '=') {
            pos++;
            return new ExprTok(orEqual, base + "=", startCol);
        }
        return new ExprTok(bare, String.valueOf(base), startCol);
    }

    /**
     * A {@code %…%} variable. The body is everything up to the next {@code %}; an
     * empty body or a missing closing {@code %} is reported but still yields a VAR
     * token (with the best-effort body) so resolution can proceed.
     */
    private ExprTok variable(int startCol) {
        pos++; // consume opening '%'
        int bodyStart = pos;
        while (pos < src.length() && src.charAt(pos) != '%') {
            pos++;
        }
        String body = src.substring(bodyStart, pos);
        if (pos >= src.length()) {
            diags.error("E_PARSE", "unterminated variable (missing closing '%')",
                    lineSource.atColumn(startCol), "close it with a '%', e.g. %victim.health%");
        } else {
            pos++; // consume closing '%'
        }
        if (body.isEmpty()) {
            diags.error("E_PARSE", "empty variable '%%'",
                    lineSource.atColumn(startCol), "name the variable, e.g. %victim.health%");
        }
        return new ExprTok(ExprTok.Kind.VAR, body, startCol);
    }

    /**
     * A quoted string, single- or double-quoted, with {@code \} escapes. The
     * returned token text is the unescaped contents (no quotes). An unterminated
     * string is reported but still yields a STRING token with what was read.
     */
    private ExprTok string(char quote, int startCol) {
        pos++; // consume opening quote
        StringBuilder sb = new StringBuilder();
        boolean closed = false;
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == '\\' && pos + 1 < src.length()) {
                sb.append(src.charAt(pos + 1)); // escaped char taken literally
                pos += 2;
            } else if (c == quote) {
                pos++; // consume closing quote
                closed = true;
                break;
            } else {
                sb.append(c);
                pos++;
            }
        }
        if (!closed) {
            diags.error("E_PARSE", "unterminated string literal",
                    lineSource.atColumn(startCol),
                    "close it with a matching " + quote + " quote");
        }
        return new ExprTok(ExprTok.Kind.STRING, sb.toString(), startCol);
    }

    /** A number: digits with an optional single decimal point (e.g. {@code 3}, {@code 1.5}). */
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

    /**
     * A bare identifier: a letter or {@code _} followed by letters, digits,
     * {@code _}, {@code -}, or {@code .}. This covers {@code true}/{@code false}
     * and enum-ish operands; the parser decides what an identifier means.
     */
    private ExprTok ident(int startCol) {
        int start = pos;
        pos++; // first char already validated by isIdentStart
        while (pos < src.length() && isIdentPart(src.charAt(pos))) {
            pos++;
        }
        return new ExprTok(ExprTok.Kind.IDENT, src.substring(start, pos), startCol);
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

    /** The 1-based column on the line for the character at index {@code idx}. */
    private int col(int idx) {
        return idx + 1;
    }
}
