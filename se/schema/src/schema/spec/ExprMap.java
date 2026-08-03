package schema.spec;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import schema.grammar.expr.Expr;

/**
 * A parsed {@link ParamType.Kind#EXPR_MAP} argument: authored token names bound to untyped numeric
 * {@link Expr} trees, in authored order. The compiler's lower stage rewrites each value to the slot-resolved
 * {@code NumExpr} IR, exactly as it does a scalar expression argument (docs/architecture.md §3.4) — this type
 * exists so that walk can find the nested expressions instead of seeing an opaque map.
 */
public record ExprMap(Map<String, Expr> entries) {

    private static final ExprMap EMPTY = new ExprMap(Map.of());

    public ExprMap {
        // unmodifiableMap over a LinkedHashMap, NOT Map.copyOf: an immutable map's iteration order is salted
        // per JVM run, and these render in the order they were authored.
        entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
    }

    /** The no-bindings value — what an absent or empty argument parses to. */
    public static ExprMap empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
