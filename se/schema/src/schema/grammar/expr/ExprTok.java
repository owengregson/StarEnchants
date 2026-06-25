package schema.grammar.expr;

/**
 * One token of the condition-expression sublanguage: a {@link Kind}, its source text, and
 * 1-based {@code col} for diagnostics. For {@link Kind#STRING}, {@code text} is the unescaped
 * contents (no quotes); for {@link Kind#VAR}, the inner body between the {@code %} delimiters;
 * other kinds carry their literal lexeme.
 */
public record ExprTok(ExprTok.Kind kind, String text, int col) {

    public enum Kind {
        NUMBER,
        IDENT,
        VAR,
        STRING,
        AND,
        OR,
        BANG,
        LPAREN,
        RPAREN,
        COMMA,
        EQ,
        NE,
        LT,
        LE,
        GT,
        GE,
        /** {@code contains} — string membership (pipe-OR alternatives). */
        CONTAINS,
        /** {@code matchesregex} — string regex full-match. */
        MATCHES_REGEX,
        /** {@code :} — clause separator between a condition's test and its flow/chance outcome. */
        COLON,
        /** {@code +} — addition, or the positive sign of a {@code ±N %chance%} delta. */
        PLUS,
        /** {@code -} — subtraction/negation, or the negative sign of a {@code ±N %chance%} delta. */
        MINUS,
        STAR,
        SLASH,
        /** End-of-input sentinel; {@code text} is empty. */
        EOF
    }
}
