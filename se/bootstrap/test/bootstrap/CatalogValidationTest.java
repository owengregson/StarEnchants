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
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import schema.diag.Diagnostic;

/**
 * The shipped catalog must compile clean through the real registries (ADR-0014; §10). Owns the
 * structural contract only; handle-token existence is version-specific and validated live by the
 * tester's {@code CatalogSuite}, so tokens resolve permissively here.
 */
class CatalogValidationTest {

    /** Accepts every handle token (id 0) — structural validation only, no server. */
    private static final PlatformResolvers PERMISSIVE = testfx.PermissiveResolvers.INSTANCE;

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
