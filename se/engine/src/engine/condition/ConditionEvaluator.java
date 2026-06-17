package engine.condition;

import compile.model.CompiledCondition;
import compile.model.cond.Cond;
import compile.model.cond.NumExpr;
import compile.model.cond.StrExpr;
import schema.grammar.expr.Cmp;

/**
 * Evaluates a compiled condition over a primitive {@link FactBuffer} to a
 * {@link ConditionResult} (docs/architecture.md §3.4). A pure tree walk: no string
 * parsing on the hot path (variables and literals are pre-resolved), no allocation
 * (results are flyweight constants), no boxing.
 *
 * <p>Semantics: numeric comparisons use IEEE ordering, so an unresolved PlaceholderAPI
 * value (parsed to {@code NaN}) fails every numeric comparison except {@code !=} —
 * gating is fail-closed on missing data. String comparison is case-insensitive and
 * null-safe.
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
        return test(condition.root(), facts) ? ConditionResult.CONTINUE : ConditionResult.STOP;
    }

    /**
     * Evaluate a boolean condition node directly to its truth value. Uses
     * {@code instanceof} pattern chains rather than a switch over the sealed type,
     * because switch type patterns are not available at the Java 17 floor (§11).
     */
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
            return containsAny(str(c.left(), f), str(c.right(), f));
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
        if (e instanceof NumExpr.Var v) {
            return f.number(v.slot());
        }
        if (e instanceof NumExpr.Lit l) {
            return l.value();
        }
        if (e instanceof NumExpr.Papi p) {
            return parseDouble(f.resolvePapi(p.raw()));
        }
        throw new IllegalStateException("unknown numeric operand: " + e);
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
     * {@code contains} membership: true if {@code haystack} contains any {@code |}-separated alternative
     * in {@code needles} (case-insensitive). A {@code null} operand or only-empty alternatives is false
     * (fail-closed on missing data, matching the numeric/placeholder semantics).
     */
    private static boolean containsAny(String haystack, String needles) {
        if (haystack == null || needles == null) {
            return false;
        }
        String hay = haystack.toLowerCase(java.util.Locale.ROOT);
        for (String alternative : needles.split("\\|")) {
            if (!alternative.isEmpty() && hay.contains(alternative.toLowerCase(java.util.Locale.ROOT))) {
                return true;
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

    private static double parseDouble(String s) {
        if (s == null) {
            return Double.NaN; // unresolved placeholder → fail-closed numeric comparisons
        }
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
}
