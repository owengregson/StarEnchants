package schema.grammar.expr;

/**
 * One token of the condition-expression sublanguage: a {@link Kind}, the source
 * text it covers, and the 1-based {@code col} of its first character on the line
 * (relative to the start of the expression text the tokenizer was given).
 *
 * <p>The column lets {@link ExprParser} attach an argument-precise
 * {@link schema.diag.Source} to a diagnostic via
 * {@link schema.diag.Source#atColumn(int)}. This is the same
 * column convention the effect-line {@link schema.grammar.Tok}
 * uses, but the token set is the expression language's, not colon segments
 * (docs/architecture.md §2).
 *
 * <p>For a {@link Kind#STRING} token, {@link #text} is the <em>unescaped</em>
 * contents (no surrounding quotes); for a {@link Kind#VAR} token, {@code text} is
 * the inner body between the {@code %} delimiters. All other kinds carry their
 * literal lexeme.
 */
public record ExprTok(ExprTok.Kind kind, String text, int col) {

    /** The lexical categories of the expression language. */
    public enum Kind {
        /** A numeric literal lexeme, e.g. {@code 3} or {@code 1.5}. */
        NUMBER,
        /** A bare identifier, e.g. {@code true}, {@code false}, or an enum-ish word. */
        IDENT,
        /** A {@code %...%} variable; {@link ExprTok#text} is the inner body. */
        VAR,
        /** A quoted string; {@link ExprTok#text} is the unescaped contents. */
        STRING,
        /** {@code &&} */
        AND,
        /** {@code ||} */
        OR,
        /** {@code !} */
        BANG,
        /** {@code (} */
        LPAREN,
        /** {@code )} */
        RPAREN,
        /** {@code ,} */
        COMMA,
        /** {@code ==} */
        EQ,
        /** {@code !=} */
        NE,
        /** {@code <} */
        LT,
        /** {@code <=} */
        LE,
        /** {@code >} */
        GT,
        /** {@code >=} */
        GE,
        /** {@code contains} — the string membership operator (pipe-OR alternatives). */
        CONTAINS,
        /** {@code matchesregex} — the string regular-expression match operator. */
        MATCHES_REGEX,
        /** {@code :} — the clause separator between a condition's test and its flow/chance outcome. */
        COLON,
        /** {@code +} — addition, or the optional positive sign of a {@code ±N %chance%} clause delta. */
        PLUS,
        /** {@code -} — subtraction / numeric negation, or the optional negative sign of a {@code ±N %chance%} clause delta. */
        MINUS,
        /** {@code *} — numeric multiplication (arithmetic operands). */
        STAR,
        /** {@code /} — numeric division (arithmetic operands). */
        SLASH,
        /** End of input — a sentinel the parser stops on; {@link ExprTok#text} is empty. */
        EOF
    }
}
