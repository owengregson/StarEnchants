package schema.grammar.expr;

import schema.diag.Diagnostics;
import schema.diag.Source;
import java.util.List;
import java.util.Optional;

/**
 * A recursive-descent (Pratt-style) parser for the condition-expression
 * sublanguage, producing the untyped {@link Expr} AST (docs/architecture.md §2,
 * §3.2, §3.4).
 *
 * <p><b>Precedence</b>, lowest to highest:
 * <pre>
 *   ||  (left-assoc, lowest)
 *   &&  (left-assoc)
 *   comparators  ( ==  !=  &lt;  &lt;=  &gt;  &gt;= )  — NON-associative
 *   !   (unary prefix)
 *   primary:  ( expr )  |  literal  |  %variable%
 * </pre>
 * Comparators are deliberately non-associative: {@code a < b < c} is reported as a
 * parse error rather than silently chained. Everything is data-only — no typing,
 * no evaluation; that is se-compile's job (docs/architecture.md §2).
 *
 * <p><b>Errors never throw.</b> A malformed input is reported into the supplied
 * {@link Diagnostics} with code {@code E_PARSE} at a precise {@link Source}, and
 * the parser recovers by synthesizing a best-effort node so a single fault yields
 * one finding rather than aborting the whole compile (matching the diagnostics
 * philosophy of the rest of se-schema). The caller checks {@code diags.hasErrors()}
 * to decide whether to use the returned tree.
 *
 * <p>{@link #parse(String, Source, Diagnostics)} returns {@link Optional#empty()}
 * only for blank input (no condition was written); any non-blank input yields a
 * tree (possibly containing recovery placeholders).
 */
public final class ExprParser {

    private final List<ExprTok> toks;
    private final Source lineSource;
    private final Diagnostics diags;
    private int idx;

    private ExprParser(List<ExprTok> toks, Source lineSource, Diagnostics diags) {
        this.toks = toks;
        this.lineSource = lineSource;
        this.diags = diags;
    }

    /**
     * Parse {@code text} into a condition-expression AST.
     *
     * @param text       the raw expression (one line; e.g. {@code %victim.health% < 5 && !%blocking%})
     * @param lineSource the source of the line the expression sits on
     * @param diags      collector for {@code E_PARSE} diagnostics
     * @return the parsed tree, or empty if {@code text} is blank
     */
    public static Optional<Expr> parse(String text, Source lineSource, Diagnostics diags) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        List<ExprTok> toks = ExprLexer.tokenize(text, lineSource, diags);
        ExprParser p = new ExprParser(toks, lineSource, diags);
        Expr e = p.parseOr();
        if (!p.atEnd()) {
            // Trailing tokens after a complete expression — e.g. "1 2" or "(a) b".
            ExprTok extra = p.peek();
            p.diags.error("E_PARSE",
                    "unexpected '" + describe(extra) + "' after the expression",
                    p.lineSource.atColumn(extra.col()),
                    "did you forget an operator like '&&', '||', or a comparator?");
        }
        return Optional.of(e);
    }

    // ----- grammar (one method per precedence level) -----

    /** {@code or := and ( "||" and )*} — lowest precedence, left-associative. */
    private Expr parseOr() {
        Expr left = parseAnd();
        while (check(ExprTok.Kind.OR)) {
            advance();
            Expr right = parseAnd();
            left = new Expr.Or(left, right, left.source());
        }
        return left;
    }

    /** {@code and := cmp ( "&&" cmp )*} — left-associative. */
    private Expr parseAnd() {
        Expr left = parseComparison();
        while (check(ExprTok.Kind.AND)) {
            advance();
            Expr right = parseComparison();
            left = new Expr.And(left, right, left.source());
        }
        return left;
    }

    /**
     * {@code cmp := unary ( comparator unary )?} — NON-associative: a second
     * comparator (a chain like {@code a < b < c}) is reported as an error, after
     * which parsing of the (best-effort) right operand still consumes the chain so
     * recovery does not loop.
     */
    private Expr parseComparison() {
        Expr left = parseUnary();
        Cmp op = comparatorAt(peek());
        if (op == null) {
            return left;
        }
        ExprTok opTok = peek();
        advance();
        Expr right = parseUnary();
        Expr cmp = new Expr.Compare(left, op, right, left.source());

        if (comparatorAt(peek()) != null) {
            ExprTok chainTok = peek();
            diags.error("E_PARSE",
                    "comparators cannot be chained ('" + chainTok.text() + "' after '"
                            + opTok.text() + "')",
                    lineSource.atColumn(chainTok.col()),
                    "split it, e.g. 'a < b && b < c'");
            // Consume the rest of the chain so the caller's loop terminates.
            while (comparatorAt(peek()) != null) {
                advance();
                parseUnary();
            }
        }
        return cmp;
    }

    /** {@code unary := "!" unary | primary} — right-recursive so {@code !!x} works. */
    private Expr parseUnary() {
        if (check(ExprTok.Kind.BANG)) {
            ExprTok bang = peek();
            advance();
            Expr operand = parseUnary();
            return new Expr.Not(operand, lineSource.atColumn(bang.col()));
        }
        return parsePrimary();
    }

    /** {@code primary := "(" or ")" | number | bool | string | variable}. */
    private Expr parsePrimary() {
        ExprTok t = peek();
        switch (t.kind()) {
            case LPAREN -> {
                advance();
                Expr inner = parseOr();
                if (check(ExprTok.Kind.RPAREN)) {
                    advance();
                } else {
                    ExprTok at = peek();
                    diags.error("E_PARSE", "missing closing ')'",
                            lineSource.atColumn(at.col()), "add a ')' to close the group");
                }
                return inner;
            }
            case NUMBER -> {
                advance();
                return new Expr.NumberLit(t.text(), lineSource.atColumn(t.col()));
            }
            case STRING -> {
                advance();
                return new Expr.StringLit(t.text(), lineSource.atColumn(t.col()));
            }
            case VAR -> {
                advance();
                return variable(t);
            }
            case IDENT -> {
                advance();
                return identifier(t);
            }
            default -> {
                // No primary where one was required (EOF, a stray operator, ')'…).
                diags.error("E_PARSE", "expected a value but found '" + describe(t) + "'",
                        lineSource.atColumn(t.col()),
                        "a value is a number, a %variable%, a \"string\", true/false, or a (group)");
                // Recover WITHOUT consuming: the caller's loop can re-synchronize on
                // the same token (e.g. a dangling operator), and a sentinel keeps the
                // tree non-null. A leading operator is consumed to avoid spinning.
                if (isOperatorLike(t)) {
                    advance();
                }
                return placeholder(t.col());
            }
        }
    }

    /**
     * Build a {@link Expr.VarRef} from a {@code %…%} token. Only the first dot
     * splits scope from name; a bare {@code %name%} (no dot) has a {@code null}
     * scope. PAPI/unknown tokens are valid here and resolved later, never rejected.
     */
    private Expr variable(ExprTok t) {
        String body = t.text();
        Source s = lineSource.atColumn(t.col());
        int dot = body.indexOf('.');
        if (dot < 0) {
            return new Expr.VarRef(null, body, s);
        }
        String scope = body.substring(0, dot);
        String name = body.substring(dot + 1);
        return new Expr.VarRef(scope, name, s);
    }

    /**
     * Resolve a bare identifier. {@code true}/{@code false} (case-insensitive)
     * become {@link Expr.BoolLit}; any other identifier is treated as an
     * (unscoped) variable name so enum-ish operands like {@code SNEAKING} survive
     * to be resolved later, rather than being rejected at parse time.
     */
    private Expr identifier(ExprTok t) {
        Source s = lineSource.atColumn(t.col());
        if (t.text().equalsIgnoreCase("true")) {
            return new Expr.BoolLit(true, s);
        }
        if (t.text().equalsIgnoreCase("false")) {
            return new Expr.BoolLit(false, s);
        }
        return new Expr.VarRef(null, t.text(), s);
    }

    // ----- token helpers -----

    private static Cmp comparatorAt(ExprTok t) {
        return switch (t.kind()) {
            case EQ -> Cmp.EQ;
            case NE -> Cmp.NE;
            case LT -> Cmp.LT;
            case LE -> Cmp.LE;
            case GT -> Cmp.GT;
            case GE -> Cmp.GE;
            default -> null;
        };
    }

    private static boolean isOperatorLike(ExprTok t) {
        return switch (t.kind()) {
            case AND, OR, BANG, COMMA, EQ, NE, LT, LE, GT, GE -> true;
            default -> false;
        };
    }

    /** A human-facing label for a token, used in diagnostic messages. */
    private static String describe(ExprTok t) {
        return t.kind() == ExprTok.Kind.EOF ? "end of expression" : t.text();
    }

    /** A non-null recovery node so a malformed tree is never {@code null}. */
    private Expr placeholder(int col) {
        return new Expr.BoolLit(false, lineSource.atColumn(col));
    }

    private ExprTok peek() {
        return toks.get(idx);
    }

    private boolean check(ExprTok.Kind kind) {
        return peek().kind() == kind;
    }

    private void advance() {
        if (idx < toks.size() - 1) { // never advance past the EOF sentinel
            idx++;
        }
    }

    private boolean atEnd() {
        return check(ExprTok.Kind.EOF);
    }
}
