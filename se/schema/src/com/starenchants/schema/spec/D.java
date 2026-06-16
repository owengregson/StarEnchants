package com.starenchants.schema.spec;

/**
 * Entry points for declaring argument types — the {@code D.DOUBLE.min(0).max(100)}
 * vocabulary used by every effect/condition/selector/trigger {@link ParamSpec}
 * (docs/architecture.md §7).
 *
 * <p>The constants are shared immutable bases; every constraint method on
 * {@link ParamType} returns a fresh instance, so reusing {@code D.DOUBLE} across
 * many params is safe.
 */
public final class D {

    private D() {
    }

    /** A finite decimal. */
    public static final ParamType DOUBLE = ParamType.of(ParamType.Kind.DOUBLE);

    /** A whole number (rejects decimals — fixes the legacy {@code getInt} truncation trap). */
    public static final ParamType INT = ParamType.of(ParamType.Kind.INT);

    /** A non-negative whole number of ticks (a typed duration). */
    public static final ParamType TICKS = ParamType.of(ParamType.Kind.TICKS);

    /** {@code true}/{@code false} (also accepts yes/no, on/off, 1/0). */
    public static final ParamType BOOL = ParamType.of(ParamType.Kind.BOOL);

    /** Free-form text. */
    public static final ParamType STRING = ParamType.of(ParamType.Kind.STRING);

    /** A closed, case-insensitive set of allowed values, normalized to the given spelling. */
    public static ParamType enumOf(String... values) {
        return ParamType.of(ParamType.Kind.ENUM).allowing(values);
    }
}
