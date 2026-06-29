package imagegen.imports;

import compile.resolve.PlatformResolvers;
import java.util.OptionalInt;

/**
 * An off-server {@link PlatformResolvers} that resolves nothing. The importer only needs the compiled def
 * catalogs (display names, set lore, tier colours), not a runtime-resolved {@code Snapshot}, so leaving every
 * volatile token unresolved is correct: the compiler warn-and-skips each unresolved effect op (never throws)
 * and the {@code Library}'s catalogs are populated regardless. This keeps {@code renderImages} deterministic
 * and serverless — the same posture as the bundled-content {@code validateContent} pass, which compiles
 * against a fake resolver for the same reason.
 */
final class StubResolvers implements PlatformResolvers {

    @Override public OptionalInt material(String token) {
        return OptionalInt.empty();
    }

    @Override public OptionalInt sound(String token) {
        return OptionalInt.empty();
    }

    @Override public OptionalInt potionEffect(String token) {
        return OptionalInt.empty();
    }

    @Override public OptionalInt particle(String token) {
        return OptionalInt.empty();
    }

    @Override public OptionalInt enchantment(String token) {
        return OptionalInt.empty();
    }

    @Override public OptionalInt entityType(String token) {
        return OptionalInt.empty();
    }

    @Override public OptionalInt attribute(String token) {
        return OptionalInt.empty();
    }
}
