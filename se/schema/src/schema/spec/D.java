package schema.spec;

/**
 * Entry points for declaring argument types — the {@code D.DOUBLE.min(0).max(100)} vocabulary
 * for every {@link ParamSpec} (docs/architecture.md §7). The constants are shared immutable bases.
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

    // Version-volatile handles: authored as a token, resolved to an interned id so the runtime never sees a renamed constant (§9).

    public static ParamType material() {
        return ParamType.handle(HandleCategory.MATERIAL);
    }

    public static ParamType sound() {
        return ParamType.handle(HandleCategory.SOUND);
    }

    public static ParamType potionEffect() {
        return ParamType.handle(HandleCategory.POTION_EFFECT);
    }

    public static ParamType particle() {
        return ParamType.handle(HandleCategory.PARTICLE);
    }

    public static ParamType entityType() {
        return ParamType.handle(HandleCategory.ENTITY_TYPE);
    }

    public static ParamType attribute() {
        return ParamType.handle(HandleCategory.ATTRIBUTE);
    }

    public static ParamType enchantment() {
        return ParamType.handle(HandleCategory.ENCHANTMENT);
    }
}
