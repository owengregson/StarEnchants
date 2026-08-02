package compile.model.cond;

import java.util.List;

/**
 * A numeric-valued operand of a compiled condition <em>or an expression-valued effect argument</em>
 * (docs/architecture.md §3.4): a resolved {@code FactBuffer} slot, a constant, a PlaceholderAPI token
 * parsed to a number at evaluation time, or an arithmetic combination of those. Pure data — the runtime
 * walks it over a primitive fact buffer with no parsing on the hot path (see the engine's {@code NumExprEval}).
 */
public sealed interface NumExpr
        permits NumExpr.Var, NumExpr.Lit, NumExpr.Papi, NumExpr.Bin, NumExpr.Neg, NumExpr.Fn {

    /** A numeric variable resolved to its dense {@code FactBuffer} number slot. */
    record Var(int slot) implements NumExpr {}

    /** A numeric literal, pre-parsed at compile time. */
    record Lit(double value) implements NumExpr {}

    /**
     * A PlaceholderAPI token used in a numeric comparison; the engine resolves the
     * placeholder and parses it to a double only when this node is reached, and only
     * if PlaceholderAPI is present (§3.4). The {@code raw} token is the original
     * {@code %...%} text (without the surrounding percents).
     */
    record Papi(String raw) implements NumExpr {}

    record Bin(NumExpr left, Op op, NumExpr right) implements NumExpr {}

    record Neg(NumExpr operand) implements NumExpr {}

    /** A function over nested operands; {@code args} arity is guaranteed by the parser's {@code ExprFn} check. */
    record Fn(FnKind kind, List<NumExpr> args) implements NumExpr {
        public Fn {
            args = List.copyOf(args);
        }
    }

    enum Op { ADD, SUBTRACT, MULTIPLY, DIVIDE }

    /** The lowered counterpart of {@code schema.grammar.expr.ExprFn} (compile owns its own IR vocabulary). */
    enum FnKind { MIN, MAX, CLAMP, FLOOR, RAND }
}
