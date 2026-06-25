package schema.grammar.expr;

import schema.diag.Diagnostics;
import schema.diag.Source;
import java.util.List;
import java.util.Optional;

/**
 * Recursive-descent parser for the condition-expression sublanguage, producing the
 * untyped {@link Expr} AST (docs/architecture.md §3.4). Never throws — faults become
 * {@code E_PARSE} diagnostics with best-effort recovery nodes. Precedence, low to high:
 * <pre>
 *   ||  (left-assoc, lowest)
 *   &&  (left-assoc)
 *   comparators  ( ==  !=  &lt;  &lt;=  &gt;  &gt;= )  — NON-associative
 *   +  -  (left-assoc)            — arithmetic, operands of a comparison
 *   *  /  (left-assoc)
 *   !  -  (unary prefix)
 *   primary:  ( expr )  |  literal  |  %variable%
 * </pre>
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

    /** Empty only for blank input; any non-blank input yields a tree (possibly with recovery placeholders). */
    public static Optional<Expr> parse(String text, Source lineSource, Diagnostics diags) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        List<ExprTok> toks = ExprLexer.tokenize(text, lineSource, diags);
        ExprParser p = new ExprParser(toks, lineSource, diags);
        Expr e = p.parseOr();
        e = p.parseClauseTail(e); // optional ": <%stop%|%force%|%allow%|%continue%|±N %chance%>" outcome
        if (!p.atEnd()) {
            ExprTok extra = p.peek();
            p.diags.error("E_PARSE",
                    "unexpected '" + describe(extra) + "' after the expression",
                    p.lineSource.atColumn(extra.col()),
                    "did you forget an operator like '&&', '||', or a comparator?");
        }
        return Optional.of(e);
    }

    /**
     * {@code clause-tail := ( ":" outcome )?} — no {@code :} returns {@code test} unchanged (bare gate).
     * A second {@code :} is an error; the rest of the line is then consumed to avoid a spurious trailing-token error.
     */
    private Expr parseClauseTail(Expr test) {
        if (!check(ExprTok.Kind.COLON)) {
            return test;
        }
        advance();
        Expr clause = parseOutcome(test);
        if (check(ExprTok.Kind.COLON)) {
            ExprTok at = peek();
            diags.error("E_PARSE", "a condition takes at most one ':' outcome clause",
                    lineSource.atColumn(at.col()),
                    "write a single '<test> : <%stop%|%force%|%allow%|±N %chance%>'");
            consumeToEnd();
        }
        return clause;
    }

    /** {@code outcome := flow-sentinel | ( ("+"|"-")? number "%chance%" )}; a malformed outcome returns the bare {@code test}. */
    private Expr parseOutcome(Expr test) {
        Source src = test.source();
        if (check(ExprTok.Kind.PLUS) || check(ExprTok.Kind.MINUS) || check(ExprTok.Kind.NUMBER)) {
            return chanceClause(test, src);
        }
        if (check(ExprTok.Kind.VAR)) {
            FlowKind flow = sentinelFlow(peek().text());
            if (flow != null) {
                advance();
                return new Expr.Clause(test, flow, 0.0, src);
            }
        }
        ExprTok at = peek();
        diags.error("E_PARSE",
                "expected a clause outcome after ':' but found '" + describe(at) + "'",
                lineSource.atColumn(at.col()),
                "use %stop%, %force%, %allow%, %continue%, or '±N %chance%'");
        consumeToEnd();
        return test;
    }

    /** {@code ("+"|"-")? number "%chance%"} — a signed percentage-point delta applied when the test passes. */
    private Expr chanceClause(Expr test, Source src) {
        double sign = 1.0;
        if (check(ExprTok.Kind.PLUS)) {
            advance();
        } else if (check(ExprTok.Kind.MINUS)) {
            sign = -1.0;
            advance();
        }
        if (!check(ExprTok.Kind.NUMBER)) {
            ExprTok at = peek();
            diags.error("E_PARSE", "expected a number before '%chance%' but found '" + describe(at) + "'",
                    lineSource.atColumn(at.col()), "write the delta, e.g. '+50 %chance%'");
            consumeToEnd();
            return test;
        }
        ExprTok numTok = peek();
        advance();
        double magnitude;
        try {
            magnitude = Double.parseDouble(numTok.text());
        } catch (NumberFormatException ex) {
            diags.error("E_PARSE", "invalid number '" + numTok.text() + "'", lineSource.atColumn(numTok.col()));
            consumeToEnd();
            return test;
        }
        if (!(check(ExprTok.Kind.VAR) && peek().text().equalsIgnoreCase("chance"))) {
            ExprTok at = peek();
            diags.error("E_PARSE", "expected '%chance%' after the delta but found '" + describe(at) + "'",
                    lineSource.atColumn(at.col()), "write the delta as '±N %chance%'");
            consumeToEnd();
            return test;
        }
        advance();
        return new Expr.Clause(test, FlowKind.CONTINUE, sign * magnitude, src);
    }

    private static FlowKind sentinelFlow(String body) {
        if (body.equalsIgnoreCase("stop")) {
            return FlowKind.STOP;
        }
        if (body.equalsIgnoreCase("force")) {
            return FlowKind.FORCE;
        }
        if (body.equalsIgnoreCase("allow")) {
            return FlowKind.ALLOW;
        }
        if (body.equalsIgnoreCase("continue")) {
            return FlowKind.CONTINUE;
        }
        return null;
    }

    /** Consume to EOF, for one-finding recovery on a malformed clause. */
    private void consumeToEnd() {
        while (!atEnd()) {
            advance();
        }
    }

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

    /** {@code cmp := unary ( comparator unary )?} — NON-associative; a chain like {@code a < b < c} is an error. */
    private Expr parseComparison() {
        Expr left = parseAdditive();
        Cmp cmpOp = comparatorAt(peek());
        StrOp strOp = strOpAt(peek());
        if (cmpOp == null && strOp == null) {
            return left;
        }
        ExprTok opTok = peek();
        advance();
        Expr right = parseAdditive();
        Expr cmp = cmpOp != null
                ? new Expr.Compare(left, cmpOp, right, left.source())
                : new Expr.StringMatch(left, strOp, right, left.source());

        if (isComparisonOp(peek())) {
            ExprTok chainTok = peek();
            diags.error("E_PARSE",
                    "comparators cannot be chained ('" + chainTok.text() + "' after '"
                            + opTok.text() + "')",
                    lineSource.atColumn(chainTok.col()),
                    "split it, e.g. 'a < b && b < c'");
            // Consume the rest of the chain so the caller's loop terminates.
            while (isComparisonOp(peek())) {
                advance();
                parseAdditive();
            }
        }
        return cmp;
    }

    /** {@code add := mul ( ("+"|"-") mul )*} — left-associative arithmetic, an operand of a comparison. */
    private Expr parseAdditive() {
        Expr left = parseMultiplicative();
        while (check(ExprTok.Kind.PLUS) || check(ExprTok.Kind.MINUS)) {
            ArithOp op = check(ExprTok.Kind.PLUS) ? ArithOp.ADD : ArithOp.SUBTRACT;
            advance();
            Expr right = parseMultiplicative();
            left = new Expr.Arith(left, op, right, left.source());
        }
        return left;
    }

    /** {@code mul := unary ( ("*"|"/") unary )*} — left-associative, binds tighter than {@code +}/{@code -}. */
    private Expr parseMultiplicative() {
        Expr left = parseUnary();
        while (check(ExprTok.Kind.STAR) || check(ExprTok.Kind.SLASH)) {
            ArithOp op = check(ExprTok.Kind.STAR) ? ArithOp.MULTIPLY : ArithOp.DIVIDE;
            advance();
            Expr right = parseUnary();
            left = new Expr.Arith(left, op, right, left.source());
        }
        return left;
    }

    /** {@code unary := "!" unary | "-" unary | primary} — right-recursive so {@code !!x} works. */
    private Expr parseUnary() {
        if (check(ExprTok.Kind.BANG)) {
            ExprTok bang = peek();
            advance();
            Expr operand = parseUnary();
            return new Expr.Not(operand, lineSource.atColumn(bang.col()));
        }
        if (check(ExprTok.Kind.MINUS)) {
            ExprTok minus = peek();
            advance();
            Expr operand = parseUnary();
            return new Expr.Neg(operand, lineSource.atColumn(minus.col()));
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
                diags.error("E_PARSE", "expected a value but found '" + describe(t) + "'",
                        lineSource.atColumn(t.col()),
                        "a value is a number, a %variable%, a \"string\", true/false, or a (group)");
                // Recover without consuming so the caller can re-synchronize; consume a leading operator to avoid spinning.
                if (isOperatorLike(t)) {
                    advance();
                }
                return placeholder(t.col());
            }
        }
    }

    /** Build a {@link Expr.VarRef}; only the first dot splits scope from name (see {@link Expr.VarRef}). */
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

    /** {@code true}/{@code false} become {@link Expr.BoolLit}; any other identifier is an unscoped variable name, resolved later. */
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

    private static StrOp strOpAt(ExprTok t) {
        return switch (t.kind()) {
            case CONTAINS -> StrOp.CONTAINS;
            case MATCHES_REGEX -> StrOp.MATCHES_REGEX;
            default -> null;
        };
    }

    private static boolean isComparisonOp(ExprTok t) {
        return comparatorAt(t) != null || strOpAt(t) != null;
    }

    private static boolean isOperatorLike(ExprTok t) {
        return switch (t.kind()) {
            case AND, OR, BANG, COMMA, EQ, NE, LT, LE, GT, GE, CONTAINS, MATCHES_REGEX,
                    COLON, PLUS, MINUS, STAR, SLASH -> true;
            default -> false;
        };
    }

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
