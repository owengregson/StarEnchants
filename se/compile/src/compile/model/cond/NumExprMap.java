package compile.model.cond;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A lowered {@code EXPR_MAP} effect argument: authored token names bound to slot-resolved {@link NumExpr}
 * trees, in authored order (docs/architecture.md §3.4). The runtime evaluates each against the activation's
 * {@code FactBuffer}; a dedicated type rather than a bare {@code Map} so the fact-mask walk and the arg
 * lowering can find the nested expressions by shape, exactly as they find a scalar {@link NumExpr}.
 */
public record NumExprMap(Map<String, NumExpr> entries) {

    private static final NumExprMap EMPTY = new NumExprMap(Map.of());

    public NumExprMap {
        // unmodifiableMap over a LinkedHashMap, NOT Map.copyOf: an immutable map's iteration order is salted
        // per JVM run, and these render in the order they were authored.
        entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
    }

    /** The no-bindings value — what an absent or empty argument lowers to. */
    public static NumExprMap empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
