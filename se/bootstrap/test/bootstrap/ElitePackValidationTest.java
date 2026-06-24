package bootstrap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.Compiler;
import compile.load.ItemsConfig;
import compile.load.ItemsLoader;
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
 * The shipped {@code elite-enchantments} config pack (ADR-0023) must be valid, loadable StarEnchants
 * content — not just well-formed YAML. This compiles the pack's {@code content/} tree through the REAL
 * registries and loads its {@code items/} folder, asserting zero blocking diagnostics, exactly as
 * {@link CatalogValidationTest} guards the default catalog. The pack source lives as a reviewable tree
 * under {@code packs-src/elite-enchantments/} and is zipped into the jar at build time; this guards the
 * source so a broken EE port can never ship.
 *
 * <p>Handle tokens resolve permissively (no server); the live suites own cross-version handle existence.
 */
class ElitePackValidationTest {

    private static final PlatformResolvers PERMISSIVE = new PlatformResolvers() {
        @Override public OptionalInt material(String token) { return OptionalInt.of(0); }
        @Override public OptionalInt sound(String token) { return OptionalInt.of(0); }
        @Override public OptionalInt potionEffect(String token) { return OptionalInt.of(0); }
        @Override public OptionalInt particle(String token) { return OptionalInt.of(0); }
        @Override public OptionalInt enchantment(String token) { return OptionalInt.of(0); }
        @Override public OptionalInt entityType(String token) { return OptionalInt.of(0); }
        @Override public OptionalInt attribute(String token) { return OptionalInt.of(0); }
    };

    private static final Path PACK = Path.of("packs-src/elite-enchantments");

    @Test
    void elitePackContentCompilesClean() {
        Path content = PACK.resolve("content");
        assertTrue(Files.isDirectory(content), "EE pack content not found from " + Path.of("").toAbsolutePath());

        Compiler compiler = ContentCompiler.production(PERMISSIVE);
        Library library = LibraryLoader.load(content, compiler, 0);

        String blocking = library.diagnostics().stream()
                .filter(Diagnostic::blocking)
                .map(Diagnostic::toString)
                .collect(Collectors.joining("\n  "));
        assertFalse(library.hasErrors(), () -> "EE pack content has blocking diagnostics:\n  " + blocking);
        // 122 enchants × multiple levels — guard against a silent empty/partial load.
        assertTrue(library.snapshot().abilityCount() > 400,
                () -> "expected the full EE catalog, got " + library.snapshot().abilityCount() + " abilities");
    }

    @Test
    void elitePackItemsLoadClean() {
        Path items = PACK.resolve("items");
        assertTrue(Files.isDirectory(items), "EE pack items not found");
        ItemsConfig config = ItemsLoader.load(items);
        String errors = config.diagnostics().stream()
                .filter(Diagnostic::blocking)
                .map(Diagnostic::toString)
                .collect(Collectors.joining("\n  "));
        assertFalse(config.hasErrors(), () -> "EE pack items have blocking diagnostics:\n  " + errors);
        assertTrue(config.soulGem().isPresent(), "the EE pack should carry a soul-gem likeness");
    }
}
