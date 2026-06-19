package compile.load;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void loadsCrystalsAsLevellessAbilitiesKeyedByTheirBaseKey(@TempDir Path root) throws IOException {
        write(root, "enchants/lifesteal.yml", """
            display: "Lifesteal"
            trigger: ATTACK
            levels:
              1: { chance: 100, effects: ["HEAL:2"] }
            """);
        write(root, "crystals/jolt.yml", """
            display: "&bJolt"
            description: "a zap on hit"
            trigger: ATTACK
            chance: 100
            effects: ["MESSAGE:zap"]
            """);

        Library lib = LibraryLoader.load(root, compiler(), 7);

        assertFalse(lib.hasErrors(), () -> lib.diagnostics().toString());
        // The crystal's stable key is the base key itself — no /level suffix (WornResolver looks it up directly).
        assertNotNull(lib.snapshot().byStableKey("crystals/jolt"));
        assertEquals(0, lib.snapshot().byStableKey("crystals/jolt").level());
        assertNotNull(lib.snapshot().byStableKey("enchants/lifesteal/1"));
        assertEquals(1, lib.crystals().size());
        assertEquals("crystals/jolt", lib.crystals().get(0).key());
        assertEquals("&bJolt", lib.displayNameOf("crystals/jolt"));
        assertEquals("Lifesteal", lib.displayNameOf("enchants/lifesteal"));
        assertNull(lib.displayNameOf("crystals/missing"));
    }

    @Test
    void loadsSetsAsSetTaggedAbilitiesCarryingThePieceThreshold(@TempDir Path root) throws IOException {
        write(root, "sets/yeti.yml", """
            display: "&bYeti"
            pieces: 4
            trigger: DEFENSE
            effects: ["HEAL:1"]
            """);

        Library lib = LibraryLoader.load(root, compiler(), 8);

        assertFalse(lib.hasErrors(), () -> lib.diagnostics().toString());
        compile.model.Ability bonus = lib.snapshot().byStableKey("sets/yeti");
        assertNotNull(bonus);
        assertEquals(compile.model.SourceKind.SET, bonus.sourceKind());
        assertEquals(4, bonus.setPieces()); // the completion threshold is erased onto the ability
        assertEquals(1, lib.sets().size());
        assertEquals("sets/yeti", lib.sets().get(0).key());
        assertEquals(4, lib.sets().get(0).pieces());
        assertEquals("&bYeti", lib.displayNameOf("sets/yeti"));
    }

    @Test
    void aSetWithNoPieceCountIsABlockingError(@TempDir Path root) throws IOException {
        write(root, "sets/broken.yml", """
            display: "Broken"
            trigger: DEFENSE
            effects: ["HEAL:1"]
            """);
        Library lib = LibraryLoader.load(root, compiler(), 9);
        assertTrue(lib.hasErrors());
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
    void requiresAnUnknownEnchantIsABlockingError(@TempDir Path root) throws IOException {
        write(root, "enchants/upgrade.yml", """
            display: "Upgrade"
            trigger: ATTACK
            requires: ["enchants/ghost"]
            levels:
              1: { chance: 100, effects: ["HEAL:1"] }
            """);
        Library lib = LibraryLoader.load(root, compiler(), 1);
        assertTrue(lib.hasErrors(), "a requires: naming a non-existent enchant must be a diagnostic");
    }

    @Test
    void blacklistsAnUnknownEnchantIsABlockingError(@TempDir Path root) throws IOException {
        write(root, "enchants/rival.yml", """
            display: "Rival"
            trigger: ATTACK
            blacklist: ["enchants/phantom"]
            levels:
              1: { chance: 100, effects: ["HEAL:1"] }
            """);
        Library lib = LibraryLoader.load(root, compiler(), 1);
        assertTrue(lib.hasErrors());
    }

    @Test
    void validRequiresAndBlacklistBetweenExistingEnchantsCompileClean(@TempDir Path root) throws IOException {
        write(root, "enchants/base.yml", """
            display: "Base"
            trigger: ATTACK
            levels:
              1: { chance: 100, effects: ["HEAL:1"] }
            """);
        write(root, "enchants/upgrade.yml", """
            display: "Upgrade"
            trigger: ATTACK
            requires: ["enchants/base"]
            blacklist: ["enchants/base"]
            levels:
              1: { chance: 100, effects: ["HEAL:2"] }
            """);
        Library lib = LibraryLoader.load(root, compiler(), 1);
        assertFalse(lib.hasErrors(), () -> lib.diagnostics().toString());
    }

    @Test
    void missingContentDirYieldsAnEmptyCleanLibrary(@TempDir Path root) {
        Library lib = LibraryLoader.load(root, compiler(), 3);
        assertFalse(lib.hasErrors());
        assertEquals(0, lib.snapshot().abilityCount());
        assertTrue(lib.catalog().isEmpty());
    }
}
