package testfx;

import compile.resolve.PlatformResolvers;
import java.util.OptionalInt;

/** The permissive {@link PlatformResolvers} the structural-validation tests share (was copy-pasted at four seams). */
public final class PermissiveResolvers {

    private PermissiveResolvers() {
    }

    /** Accepts every handle token (id 0) — structural validation only, no server; existence is CatalogSuite's job. */
    public static final PlatformResolvers INSTANCE = new PlatformResolvers() {
        @Override public OptionalInt material(String token) { return OptionalInt.of(0); }
        @Override public OptionalInt sound(String token) { return OptionalInt.of(0); }
        @Override public OptionalInt potionEffect(String token) { return OptionalInt.of(0); }
        @Override public OptionalInt particle(String token) { return OptionalInt.of(0); }
        @Override public OptionalInt enchantment(String token) { return OptionalInt.of(0); }
        @Override public OptionalInt entityType(String token) { return OptionalInt.of(0); }
        @Override public OptionalInt attribute(String token) { return OptionalInt.of(0); }
    };
}
