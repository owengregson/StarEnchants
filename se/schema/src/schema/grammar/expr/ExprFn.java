package schema.grammar.expr;

import java.util.Locale;

/**
 * The closed set of numeric functions callable in the expression sublanguage (docs/architecture.md §3.4).
 * Arity is fixed per function, so a call's shape is checked at parse time rather than deferred to lowering.
 */
public enum ExprFn {

    MIN(2),
    MAX(2),
    /** {@code clamp(x, lo, hi)} — {@code x} confined to the inclusive span; a reversed span yields {@code lo}. */
    CLAMP(3),
    /** {@code floor(x)} — toward negative infinity, so {@code floor(-2.1)} is {@code -3}. */
    FLOOR(1),
    /** {@code rand(lo, hi)} — uniform in {@code [lo, hi)} from the evaluator's injected random source. */
    RAND(2);

    private final int arity;

    ExprFn(int arity) {
        this.arity = arity;
    }

    /** The lower-case name authors write. */
    public String token() {
        return name().toLowerCase(Locale.ROOT);
    }

    public int arity() {
        return arity;
    }

    /** The function an identifier names, or {@code null} if it names none (case-insensitive). */
    public static ExprFn lookup(String name) {
        for (ExprFn fn : values()) {
            if (fn.name().equalsIgnoreCase(name)) {
                return fn;
            }
        }
        return null;
    }
}
