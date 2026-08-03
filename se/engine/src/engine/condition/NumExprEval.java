package engine.condition;

import compile.model.cond.NumExpr;
import java.util.List;

/**
 * Evaluates a compiled {@link NumExpr} to a {@code double} over a {@link FactBuffer} (docs/architecture.md
 * §3.4). The one numeric-expression walker shared by condition operands and expression-valued effect
 * arguments.
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
        if (e instanceof NumExpr.EntityVar v) {
            // Unset/no victim reads 0, not NaN: a counter that hasn't started is zero stacks, not "unknown".
            double parsed = parseDouble(f.resolveVictimVar(v.name()));
            return Double.isNaN(parsed) ? 0.0 : parsed;
        }
        if (e instanceof NumExpr.PotionLevel p) {
            // amplifier+1, 0 when absent — so `> 0` reads "active" and `> 1` reads "at least II".
            return p.scope() == NumExpr.Scope.VICTIM
                    ? f.victimPotionLevel(p.handleId())
                    : f.actorPotionLevel(p.handleId());
        }
        if (e instanceof NumExpr.Fn fn) {
            return function(fn, f);
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

    /** Arity is guaranteed by the parser, so each arm indexes its operands directly. */
    private static double function(NumExpr.Fn fn, FactBuffer f) {
        List<NumExpr> args = fn.args();
        return switch (fn.kind()) {
            case MIN -> Math.min(eval(args.get(0), f), eval(args.get(1), f));
            case MAX -> Math.max(eval(args.get(0), f), eval(args.get(1), f));
            case CLAMP -> clamp(eval(args.get(0), f), eval(args.get(1), f), eval(args.get(2), f));
            case FLOOR -> Math.floor(eval(args.get(0), f));
            // [lo,hi) from the buffer's injected source; a reversed span is empty and reads lo.
            case RAND -> {
                double lo = eval(args.get(0), f);
                double hi = eval(args.get(1), f);
                yield hi <= lo ? lo : lo + f.random() * (hi - lo);
            }
        };
    }

    /** A reversed span reads {@code lo} — the author's stated floor wins over a mistyped ceiling. */
    private static double clamp(double v, double lo, double hi) {
        return hi < lo ? lo : Math.min(Math.max(v, lo), hi);
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
