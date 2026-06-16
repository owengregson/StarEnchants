package compile.load;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.Compiler;
import compile.MapSpecRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import schema.spec.D;
import schema.spec.ParamSpec;

/**
 * Unit tests for the directory loader (ADR-0014): a {@code content/} tree compiles to a
 * {@link Library} whose {@code Snapshot} resolves the path-derived per-level keys, a malformed file
 * is reported without sinking the valid content, and a missing tree is an empty (clean) library.
 * Uses a real {@link Compiler} wired with a tiny effect-spec registry — no server.
 */
class LibraryLoaderTest {

    private static Compiler compiler() {
        return Compiler.of(MapSpecRegistry.of(
                ParamSpec.of("HEAL").param("amount", D.DOUBLE.min(0)).build(),
                ParamSpec.of("MESSAGE").param("text", D.STRING).build()));
    }

    private static void write(Path root, String relative, String yaml) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, yaml, StandardCharsets.UTF_8);
    }

    @Test
    void loadsADirectoryTreeIntoASnapshotAndCatalog(@TempDir Path root) throws IOException {
        write(root, "enchants/lifesteal.yml", """
            display: "&cLifesteal"
            trigger: ATTACK
            levels:
              1: { chance: 10, effects: ["HEAL:2"] }
              2: { chance: 20, effects: ["HEAL:4"] }
            """);
        write(root, "enchants/herald.yml", """
            display: "Herald"
            trigger: ATTACK
            levels:
              1: { chance: 100, effects: ["MESSAGE:hi"] }
            """);

        Library lib = LibraryLoader.load(root, compiler(), 1);

        assertFalse(lib.hasErrors(), () -> lib.diagnostics().toString());
        assertEquals(1, lib.snapshot().generation());
        assertNotNull(lib.snapshot().byStableKey("enchants/lifesteal/1"));
        assertNotNull(lib.snapshot().byStableKey("enchants/lifesteal/2"));
        assertNotNull(lib.snapshot().byStableKey("enchants/herald/1"));
        assertEquals(2, lib.catalog().size());
    }

    @Test
    void aMalformedFileIsReportedButValidContentStillLoads(@TempDir Path root) throws IOException {
        write(root, "enchants/good.yml", """
            trigger: ATTACK
            levels:
              1: { chance: 50, effects: ["HEAL:2"] }
            """);
        write(root, "enchants/bad.yml", "trigger: ATTACK\n"); // no levels -> a blocking diagnostic

        Library lib = LibraryLoader.load(root, compiler(), 2);

        assertTrue(lib.hasErrors());
        assertNotNull(lib.snapshot().byStableKey("enchants/good/1"));
    }

    @Test
    void aFileWithNoNameStemIsReportedNotKeyedEmpty(@TempDir Path root) throws IOException {
        // A file literally named ".yml" would yield an empty/degenerate base key — reject it.
        write(root, "enchants/.yml", """
            trigger: ATTACK
            levels:
              1: { chance: 50, effects: ["HEAL:2"] }
            """);
        Library lib = LibraryLoader.load(root, compiler(), 4);
        assertTrue(lib.hasErrors());
        assertEquals(0, lib.snapshot().abilityCount());
    }

    @Test
    void missingContentDirYieldsAnEmptyCleanLibrary(@TempDir Path root) {
        Library lib = LibraryLoader.load(root, compiler(), 3);
        assertFalse(lib.hasErrors());
        assertEquals(0, lib.snapshot().abilityCount());
        assertTrue(lib.catalog().isEmpty());
    }
}
