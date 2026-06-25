package engine.condition;

import compile.model.cond.NumExpr;

/**
 * Evaluates a compiled {@link NumExpr} to a {@code double} over a primitive {@link FactBuffer}
 * (docs/architecture.md §3.4). The one numeric-expression walker shared by condition operands
 * ({@link ConditionEvaluator}) and expression-valued effect arguments (the runtime {@code EffectCtx}).
 * Variables and literals are pre-resolved at compile time, so the hot path does no parsing (only an
 * unresolved PlaceholderAPI token, and only if reached), no allocation, no boxing.
 *
 * <p>Fail-safe arithmetic: an unresolved placeholder reads {@code NaN} (numeric comparisons then fail
 * closed), and division by zero yields {@code 0} rather than {@code NaN}/an exception — a scaled effect
 * argument degrades to "no contribution" instead of poisoning the damage fold with {@code NaN}.
 */
public final class NumExprEval {

    private NumExprEval() {
    }

    public static double eval(NumExpr e, FactBuffer f) {
        if (e instanceof NumExpr.Var v) {
            return f.number(v.slot());
        }
        if (e instanceof NumExpr.Lit l) {
            return l.value();
        }
        if (e instanceof NumExpr.Papi p) {
            return parseDouble(f.resolvePapi(p.raw()));
        }
        if (e instanceof NumExpr.Neg n) {
            return -eval(n.operand(), f);
        }
        if (e instanceof NumExpr.Bin b) {
            double l = eval(b.left(), f);
            double r = eval(b.right(), f);
            return switch (b.op()) {
                case ADD -> l + r;
                case SUBTRACT -> l - r;
                case MULTIPLY -> l * r;
                case DIVIDE -> r == 0.0 ? 0.0 : l / r;
            };
        }
        throw new IllegalStateException("unknown numeric operand: " + e);
    }

    /** Parse a placeholder result as a double; absent/unparseable is {@code NaN} (fail-closed comparisons). */
    static double parseDouble(String s) {
        if (s == null) {
            return Double.NaN;
        }
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
}
