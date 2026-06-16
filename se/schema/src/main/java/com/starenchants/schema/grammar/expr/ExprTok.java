package com.starenchants.schema.grammar.expr;

/**
 * One token of the condition-expression sublanguage: a {@link Kind}, the source
 * text it covers, and the 1-based {@code col} of its first character on the line
 * (relative to the start of the expression text the tokenizer was given).
 *
 * <p>The column lets {@link ExprParser} attach an argument-precise
 * {@link com.starenchants.schema.diag.Source} to a diagnostic via
 * {@link com.starenchants.schema.diag.Source#atColumn(int)}. This is the same
 * column convention the effect-line {@link com.starenchants.schema.grammar.Tok}
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
        /** End of input — a sentinel the parser stops on; {@link ExprTok#text} is empty. */
        EOF
    }
}
