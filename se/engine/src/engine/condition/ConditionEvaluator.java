package engine.condition;

import compile.model.CompiledCondition;
import compile.model.cond.Cond;
import compile.model.cond.NumExpr;
import compile.model.cond.StrExpr;
import schema.grammar.expr.Cmp;
import schema.grammar.expr.FlowKind;

/**
 * Evaluates a compiled condition over a primitive {@link FactBuffer} (docs/architecture.md §3.4).
 *
 * <p>Numeric comparisons use IEEE ordering, so an unresolved PlaceholderAPI value (parsed to {@code NaN})
 * fails every numeric comparison except {@code !=} — gating is fail-closed on missing data. String
 * comparison is case-insensitive and null-safe.
 */
public final class ConditionEvaluator {

    private ConditionEvaluator() {
    }

    /**
     * Evaluate a (possibly {@code null}) compiled condition. A {@code null} condition
     * is "always true" → {@link ConditionResult#CONTINUE}.
     */
    public static ConditionResult eval(CompiledCondition condition, FactBuffer facts) {
        if (condition == null) {
            return ConditionResult.CONTINUE;
        }
        if (test(condition.root(), facts)) {
            return ConditionResult.of(flow(condition.whenTrue()), condition.chanceDelta());
        }
        return ConditionResult.of(flow(condition.whenFalse()), 0.0);
    }

    private static Flow flow(FlowKind kind) {
        return switch (kind) {
            case CONTINUE -> Flow.CONTINUE;
            case STOP -> Flow.STOP;
            case FORCE -> Flow.FORCE;
            case ALLOW -> Flow.ALLOW;
        };
    }

    // instanceof chains, not a switch over the sealed type: type patterns aren't at the Java 17 floor (§11).
    public static boolean test(Cond node, FactBuffer f) {
        if (node instanceof Cond.And a) {
            return test(a.left(), f) && test(a.right(), f);
        }
        if (node instanceof Cond.Or o) {
            return test(o.left(), f) || test(o.right(), f);
        }
        if (node instanceof Cond.Not n) {
            return !test(n.operand(), f);
        }
        if (node instanceof Cond.NumCmp c) {
            return compareNum(num(c.left(), f), c.op(), num(c.right(), f));
        }
        if (node instanceof Cond.StrCmp c) {
            return c.equal() == equalsStr(str(c.left(), f), str(c.right(), f));
        }
        if (node instanceof Cond.BoolCmp c) {
            return c.equal() == (test(c.left(), f) == test(c.right(), f));
        }
        if (node instanceof Cond.StrContains c) {
            return containsAny(str(c.left(), f), c.alternatives());
        }
        if (node instanceof Cond.Regex c) {
            String value = str(c.left(), f);
            return value != null && c.pattern().matcher(value).matches();
        }
        if (node instanceof Cond.BoolVar v) {
            return f.flag(v.slot());
        }
        if (node instanceof Cond.BoolLit l) {
            return l.value();
        }
        if (node instanceof Cond.BoolPapi p) {
            return truthy(f.resolvePapi(p.raw()));
        }
        throw new IllegalStateException("unknown condition node: " + node);
    }

    private static double num(NumExpr e, FactBuffer f) {
        return NumExprEval.eval(e, f);
    }

    private static String str(StrExpr e, FactBuffer f) {
        if (e instanceof StrExpr.Var v) {
            return f.string(v.slot());
        }
        if (e instanceof StrExpr.Lit l) {
            return l.value();
        }
        if (e instanceof StrExpr.Papi p) {
            return f.resolvePapi(p.raw());
        }
        throw new IllegalStateException("unknown string operand: " + e);
    }

    private static boolean compareNum(double a, Cmp op, double b) {
        return switch (op) {
            case EQ -> a == b;
            case NE -> a != b;
            case LT -> a < b;
            case LE -> a <= b;
            case GT -> a > b;
            case GE -> a >= b;
        };
    }

    private static boolean equalsStr(String a, String b) {
        return a == null ? b == null : a.equalsIgnoreCase(b);
    }

    /**
     * True if {@code haystack} contains any pre-lowered {@code alternative} (case-insensitive substring); a
     * null haystack is fail-closed false. Alternatives are split + lower-cased at compile ({@link Cond.StrContains}),
     * so this is an allocation-free {@code regionMatches} scan — no {@code split}/{@code toLowerCase} on the hot path.
     */
    private static boolean containsAny(String haystack, String[] alternatives) {
        if (haystack == null) {
            return false;
        }
        for (String alternative : alternatives) {
            int last = haystack.length() - alternative.length();
            for (int i = 0; i <= last; i++) {
                if (haystack.regionMatches(true, i, alternative, 0, alternative.length())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Read a placeholder result as a boolean; absent/unrecognised is {@code false} (fail-closed). */
    private static boolean truthy(String s) {
        if (s == null) {
            return false;
        }
        String t = s.trim();
        return t.equalsIgnoreCase("true") || t.equalsIgnoreCase("yes")
                || t.equalsIgnoreCase("on") || t.equals("1");
    }
}
