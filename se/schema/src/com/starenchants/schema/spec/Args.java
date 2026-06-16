package com.starenchants.schema.spec;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The typed result of validating a DSL line against a {@link ParamSpec}: argument
 * name → already-parsed value ({@link Double}, {@link Long}, {@link Boolean} or
 * {@link String}).
 *
 * <p>This is the bridge from authored text to the compiled runtime. Effect/
 * condition implementations read it by name with no parsing on the hot path
 * (docs/architecture.md §7); the compiler lowers these values into the typed-args
 * record of a {@code CompiledEffect}.
 *
 * <p>An {@code Args} is only meaningful when validation produced no errors. When
 * a value failed to parse it is simply absent — callers that have already checked
 * {@link com.starenchants.schema.diag.Diagnostics#hasErrors()} can read present
 * values safely; the typed accessors throw on a missing/mismatched key to surface
 * programming errors loudly.
 */
public final class Args {

    private final Map<String, Object> values;

    Args(Map<String, Object> values) {
        this.values = new LinkedHashMap<>(values);
    }

    /** An empty argument set (e.g. a head with no parameters). */
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

    /** A whole-number argument narrowed to {@code int}. */
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

    /** An immutable view of every parsed value, in declaration order. */
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
