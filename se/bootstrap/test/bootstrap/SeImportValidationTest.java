package bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.Compiler;
import compile.load.ContentHolder;
import compile.load.Library;
import compile.load.LibraryLoader;
import compile.resolve.PlatformResolvers;
import engine.boot.ContentCompiler;
import feature.imports.ImportCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;
import platform.content.ContentReloader;
import platform.content.ReloadResult;

/**
 * {@code /se import}'s validate-before-write contract (ADR-0029): a candidate is run through the REAL
 * compiler ({@link ContentReloader#validateCandidate}) against the live tree in a throwaway directory, so a
 * faulty enchant reports blocking diagnostics and the live {@code content/} folder is never touched. Mirrors
 * the structural-validation seam of {@link CatalogValidationTest} (permissive handle resolver, no server).
 */
class SeImportValidationTest {

    private static final PlatformResolvers PERMISSIVE = new PlatformResolvers() {
        @Override public OptionalInt material(String token) { return OptionalInt.of(0); }
        @Override public OptionalInt sound(String token) { return OptionalInt.of(0); }
        @Override public OptionalInt potionEffect(String token) { return OptionalInt.of(0); }
        @Override public OptionalInt particle(String token) { return OptionalInt.of(0); }
        @Override public OptionalInt enchantment(String token) { return OptionalInt.of(0); }
        @Override public OptionalInt entityType(String token) { return OptionalInt.of(0); }
        @Override public OptionalInt attribute(String token) { return OptionalInt.of(0); }
    };

    private static ContentReloader reloaderOver(Path contentRoot) throws IOException {
        Compiler compiler = ContentCompiler.production(PERMISSIVE);
        Files.createDirectories(contentRoot);
        Library seed = LibraryLoader.load(contentRoot, compiler, 0);
        return new ContentReloader(new ContentHolder(seed), () -> compiler, contentRoot, 0);
    }

    private static String yaml(Map<String, Object> content) {
        return ImportCode.toYaml(content);
    }

    @Test
    void aValidEnchantValidatesCleanAndWritesNoFile(@TempDir Path contentRoot) throws IOException {
        ContentReloader reloader = reloaderOver(contentRoot);

        Map<String, Object> level = new LinkedHashMap<>();
        level.put("chance", 25);
        level.put("effects", List.of(Map.of("POTION",
                new LinkedHashMap<>(Map.of("effect", "SLOWNESS", "level", 1, "duration", 60, "who", "@Victim")))));
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("display", "&bFrostbite");
        content.put("trigger", "ATTACK");
        content.put("applies-to", List.of("SWORD"));
        content.put("levels", Map.of(1, level));

        ReloadResult result = reloader.validateCandidate("enchants/frostbite.yml", yaml(content));

        assertEquals(0, result.errorCount(), () -> "valid enchant should validate clean: " + result.diagnostics());
        assertFalse(result.published(), "validate never publishes");
        assertNoEnchantFiles(contentRoot); // the live tree was never touched — only a throwaway copy
    }

    @Test
    void anInvalidEnchantAbortsWithDiagnosticsAndWritesNoFile(@TempDir Path contentRoot) throws IOException {
        ContentReloader reloader = reloaderOver(contentRoot);

        Map<String, Object> level = new LinkedHashMap<>();
        level.put("chance", 25);
        level.put("effects", List.of(Map.of("NOT_A_REAL_EFFECT", Map.of("x", 1))));
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("display", "&cBroken");
        content.put("trigger", "TOTALLY_BOGUS_TRIGGER"); // unknown trigger → a blocking diagnostic
        content.put("applies-to", List.of("SWORD"));
        content.put("levels", Map.of(1, level));

        ReloadResult result = reloader.validateCandidate("enchants/broken.yml", yaml(content));

        assertTrue(result.errorCount() > 0, "an invalid enchant must report blocking diagnostics");
        assertFalse(result.published(), "validate never publishes");
        assertNoEnchantFiles(contentRoot); // disk untouched on failure — the import command must abort here
    }

    private static void assertNoEnchantFiles(Path contentRoot) throws IOException {
        Path enchants = contentRoot.resolve("enchants");
        if (!Files.isDirectory(enchants)) {
            return;
        }
        try (var walk = Files.walk(enchants)) {
            assertTrue(walk.filter(Files::isRegularFile).findAny().isEmpty(),
                    "validation must not write into the live content tree");
        }
    }
}
