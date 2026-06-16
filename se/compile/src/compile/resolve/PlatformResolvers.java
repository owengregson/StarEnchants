package compile.resolve;

import java.util.OptionalInt;

/**
 * The Bukkit-free seam through which the compiler resolves version-volatile names
 * to stable interned handles — <em>at compile time</em> (docs/architecture.md
 * §2.1, §9).
 *
 * <p>The compiler must stay pure and deterministically testable, but it still has
 * to turn authored tokens like {@code CONFUSION}, {@code PROTECTION_ENVIRONMENTAL},
 * or {@code PIG_ZOMBIE} into resolved handles. It does so through this injected
 * facade: production wires in {@code se-platform/resolve} (modern → legacy alias →
 * Registry → warn+skip); unit tests pass a fake. The runtime is then
 * constitutionally incapable of touching a renamed constant — it only ever sees
 * the interned ids produced here.
 *
 * <p>Each method returns the dense interned id for a resolved token, or an empty
 * {@link OptionalInt} when the token is unknown on the target platform — in which
 * case the compiler emits a file/line diagnostic and warn-and-skips that one op;
 * the load never crashes.
 */
public interface PlatformResolvers {

    OptionalInt material(String token);

    OptionalInt sound(String token);

    OptionalInt potionEffect(String token);

    OptionalInt particle(String token);

    OptionalInt enchantment(String token);

    OptionalInt entityType(String token);

    OptionalInt attribute(String token);

    /**
     * A resolver that knows no tokens — every lookup is empty. Used where a compile
     * has no version-volatile handles to resolve (so the resolve stage is a no-op),
     * and as a convenient base in tests.
     */
    static PlatformResolvers none() {
        return new PlatformResolvers() {
            @Override public OptionalInt material(String token) { return OptionalInt.empty(); }
            @Override public OptionalInt sound(String token) { return OptionalInt.empty(); }
            @Override public OptionalInt potionEffect(String token) { return OptionalInt.empty(); }
            @Override public OptionalInt particle(String token) { return OptionalInt.empty(); }
            @Override public OptionalInt enchantment(String token) { return OptionalInt.empty(); }
            @Override public OptionalInt entityType(String token) { return OptionalInt.empty(); }
            @Override public OptionalInt attribute(String token) { return OptionalInt.empty(); }
        };
    }
}
