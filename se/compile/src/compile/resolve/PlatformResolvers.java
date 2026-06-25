package compile.resolve;

import java.util.OptionalInt;

/**
 * The Bukkit-free seam through which the compiler resolves version-volatile names to stable interned
 * handles <em>at compile time</em> (docs/architecture.md §2.1, §9). Injection keeps the compiler pure:
 * production wires {@code se-platform/resolve} (modern &rarr; legacy alias &rarr; Registry &rarr;
 * warn+skip), tests a fake — so the runtime only ever sees the interned ids produced here.
 *
 * <p>Each method returns the dense interned id, or empty {@link OptionalInt} when the token is unknown on
 * the target platform — the compiler then diagnoses and warn-and-skips that one op.
 */
public interface PlatformResolvers {

    OptionalInt material(String token);

    OptionalInt sound(String token);

    OptionalInt potionEffect(String token);

    OptionalInt particle(String token);

    OptionalInt enchantment(String token);

    OptionalInt entityType(String token);

    OptionalInt attribute(String token);

    /** A resolver that knows no tokens — every lookup is empty (resolve stage becomes a no-op; tests). */
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
