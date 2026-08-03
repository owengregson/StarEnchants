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
    /**
     * A string-keyed set of numeric expressions, authored as a nested map ({@code tokens: { souls: "%actor.souls%" }})
     * or the equivalent flat {@code "souls=%actor.souls%"} scalar. Optional, defaulting to no bindings.
     */
    public static ParamType exprMap() {
        return ParamType.of(ParamType.Kind.EXPR_MAP).def("");
    }

    public static ParamType enumOf(String... values) {
        return ParamType.of(ParamType.Kind.ENUM).allowing(values);
    }

    /**
     * Like {@link #enumOf} but a value may also be a {@code A+B} CONJUNCTION of the allowed values
     * ({@code ENEMIES+PLAYERS} = only what both admit).
     */
    public static ParamType enumSetOf(String... values) {
        return ParamType.enumSet().allowing(values);
    }

    // Version-volatile handles: authored as a token, resolved to an interned id so the runtime never sees a renamed constant (§9).

    public static ParamType material() {
        return ParamType.handle(HandleCategory.MATERIAL);
    }

    /** A comma-separated set of material tokens ({@code "[STONE,DIRT]"} inside a selector body), each interned at compile. */
    public static ParamType materials() {
        return ParamType.handleList(HandleCategory.MATERIAL);
    }

    public static ParamType sound() {
        return ParamType.handle(HandleCategory.SOUND);
    }

    public static ParamType potionEffect() {
        return ParamType.handle(HandleCategory.POTION_EFFECT);
    }

    /** A comma-separated set of potion-effect tokens ({@code "SPEED, REGENERATION"}), each interned at compile. */
    public static ParamType potionEffects() {
        return ParamType.handleList(HandleCategory.POTION_EFFECT);
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
