package bootstrap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.Compiler;
import compile.load.Library;
import compile.load.LibraryLoader;
import compile.resolve.PlatformResolvers;
import engine.boot.ContentCompiler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import schema.diag.Diagnostic;

/**
 * The shipped content catalog must compile clean (ADR-0014; §10). This loads every authored
 * {@code resources/content/} file through the REAL effect/selector/trigger registries — so an
 * unknown effect head, a wrong arg count/type, an unknown trigger, a malformed level, a missing
 * set {@code pieces}, or broken YAML is caught at build time, before the catalog ever ships.
 *
 * <p>Handle tokens (potion/particle/sound/entity names) resolve through a PERMISSIVE resolver here:
 * their real cross-version existence is version-specific and is validated live on each server by the
 * tester's {@code CatalogSuite}. This test owns the structural contract; the live suite owns handles.
 */
class CatalogValidationTest {

    /** A resolver that accepts every handle token (id 0) — structural validation only; no server. */
    private static final PlatformResolvers PERMISSIVE = new PlatformResolvers() {
        @Override public OptionalInt material(String token) { return OptionalInt.of(0); }
        @Override public OptionalInt sound(String token) { return OptionalInt.of(0); }
        @Override public OptionalInt potionEffect(String token) { return OptionalInt.of(0); }
        @Override public OptionalInt particle(String token) { return OptionalInt.of(0); }
        @Override public OptionalInt enchantment(String token) { return OptionalInt.of(0); }
        @Override public OptionalInt entityType(String token) { return OptionalInt.of(0); }
        @Override public OptionalInt attribute(String token) { return OptionalInt.of(0); }
    };

    @Test
    void shippedCatalogCompilesWithNoStructuralErrors() {
        Path content = Path.of("resources/content");
        assertTrue(Files.isDirectory(content), "content dir not found from " + Path.of("").toAbsolutePath());

        Compiler compiler = ContentCompiler.production(PERMISSIVE);
        Library library = LibraryLoader.load(content, compiler, 0);

        String blocking = library.diagnostics().stream()
                .filter(Diagnostic::blocking)
                .map(Diagnostic::toString)
                .collect(Collectors.joining("\n  "));
        assertFalse(library.hasErrors(), () -> "catalog has blocking diagnostics:\n  " + blocking);

        // Guard against a silent empty load (wrong CWD) — the catalog is large.
        assertTrue(library.snapshot().abilityCount() > 60,
                () -> "expected a large catalog, got " + library.snapshot().abilityCount() + " abilities");
    }
}
