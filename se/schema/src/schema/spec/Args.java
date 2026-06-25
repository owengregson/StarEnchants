package schema.spec;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Typed result of validating a DSL line against a {@link ParamSpec}: argument name →
 * parsed value (docs/architecture.md §7). Only meaningful when validation produced no
 * errors; a failed value is simply absent. Typed accessors throw on a missing key.
 */
public final class Args {

    private final Map<String, Object> values;

    Args(Map<String, Object> values) {
        this.values = new LinkedHashMap<>(values);
    }

    public static Args empty() {
        return new Args(Map.of());
    }

    public boolean has(String name) {
        return values.containsKey(name);
    }

    public double dbl(String name) {
        return ((Number) require(name)).doubleValue();
    }

    public long lng(String name) {
        return ((Number) require(name)).longValue();
    }

    public int integer(String name) {
        return Math.toIntExact(lng(name));
    }

    public boolean bool(String name) {
        return (Boolean) require(name);
    }

    public String str(String name) {
        return String.valueOf(require(name));
    }

    public Optional<Object> opt(String name) {
        return Optional.ofNullable(values.get(name));
    }

    /** A copy with {@code name} set; resolve uses it to rewrite a handle token to its interned int (§9), immutably. */
    public Args with(String name, Object value) {
        Map<String, Object> copy = new LinkedHashMap<>(values);
        copy.put(name, value);
        return new Args(copy);
    }

    /** Immutable view, in declaration order. */
    public Map<String, Object> asMap() {
        return Collections.unmodifiableMap(values);
    }

    private Object require(String name) {
        Object v = values.get(name);
        if (v == null) {
            throw new IllegalArgumentException("no argument '" + name + "' (present: " + values.keySet() + ")");
        }
        return v;
    }

    @Override
    public String toString() {
        return "Args" + values;
    }
}
