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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import schema.diag.DiagCode;
import schema.diag.Diagnostic;
import schema.spec.D;
import schema.spec.ParamSpec;

/**
 * Content-format tests run end to end through the real {@link Compiler} and {@link LibraryLoader}.
 * The one authoring shape: every level declared explicitly under {@code levels:}; a {@code $} is
 * always a literal (no scale/token grammar).
 */
class ContentFormatTest {

    private static Compiler compiler() {
        return Compiler.of(MapSpecRegistry.of(
                ParamSpec.of("HEAL").param("amount", D.DOUBLE.min(0)).build(),
                ParamSpec.of("MESSAGE").param("text", D.STRING).build(),
                ParamSpec.of("POTION")
                        .param("effect", D.STRING)
                        .param("amplifier", D.INT.min(0))
                        .param("duration", D.TICKS)
                        .build()));
    }

    private static void write(Path root, String relative, String yaml) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, yaml, StandardCharsets.UTF_8);
    }

    private static boolean hasCode(List<Diagnostic> diags, DiagCode code) {
        return diags.stream().anyMatch(d -> d.is(code));
    }

    @Test
    void terseEffectStringIsRejected(@TempDir Path root) throws IOException {
        // ADR-0016 update: the terse colon form is no longer an authorable content syntax — a scalar effect
        // item is a fatal E_TERSE_EFFECT diagnostic; only the block { HEAD: { ... } } form compiles.
        write(root, "enchants/terse.yml", """
            trigger: ATTACK
            levels:
              1: { chance: 100, effects: ["POTION:STRENGTH:1:100"] }
            """);
        Library lib = LibraryLoader.load(root, compiler(), 1);

        assertTrue(lib.hasErrors(), () -> lib.diagnostics().toString());
        assertTrue(hasCode(lib.diagnostics(), DiagCode.E_TERSE_EFFECT), () -> lib.diagnostics().toString());
    }

    @Test
    void verboseEffectCompilesToNamedArgs(@TempDir Path root) throws IOException {
        write(root, "enchants/verbose.yml", """
            trigger: ATTACK
            levels:
              1: { chance: 100, effects: [ { POTION: { effect: STRENGTH, amplifier: 1, duration: 100 } } ] }
            """);
        Library lib = LibraryLoader.load(root, compiler(), 1);

        assertFalse(lib.hasErrors(), () -> lib.diagnostics().toString());
        var verbose = lib.snapshot().byStableKey("enchants/verbose/1").effects()[0].args();
        assertEquals("STRENGTH", verbose.str("effect"));
        assertEquals(1, verbose.integer("amplifier"));
        assertEquals(100, verbose.integer("duration"));
    }

    @Test
    void verboseStringArgWithAColonIsNotShredded(@TempDir Path root) throws IOException {
        // B2 regression: a verbose string is one whole arg — a ':' inside it must survive
        write(root, "enchants/msg.yml", """
            trigger: ATTACK
            levels:
              1: { chance: 100, effects: [ { MESSAGE: { text: "Combo x2: keep it up!" } } ] }
            """);
        Library lib = LibraryLoader.load(root, compiler(), 1);

        assertFalse(lib.hasErrors(), () -> lib.diagnostics().toString());
        assertEquals("Combo x2: keep it up!",
                lib.snapshot().byStableKey("enchants/msg/1").effects()[0].args().str("text"));
    }

    @Test
    void verboseStringArgStartingWithAtIsNotMistakenForASelector(@TempDir Path root) throws IOException {
        // M5 regression: a value starting with '@' is text, never peeled as a target selector
        write(root, "enchants/at.yml", """
            trigger: ATTACK
            levels:
              1: { chance: 100, effects: [ { MESSAGE: { text: "@everyone listen up" } } ] }
            """);
        Library lib = LibraryLoader.load(root, compiler(), 1);

        assertFalse(lib.hasErrors(), () -> lib.diagnostics().toString());
        assertEquals("@everyone listen up",
                lib.snapshot().byStableKey("enchants/at/1").effects()[0].args().str("text"));
    }

    @Test
    void verboseMissingRequiredParamNamesTheParam(@TempDir Path root) throws IOException {
        write(root, "enchants/bad.yml", """
            trigger: ATTACK
            levels:
              1: { chance: 100, effects: [ { POTION: { effect: STRENGTH, duration: 100 } } ] }
            """);
        Library lib = LibraryLoader.load(root, compiler(), 1);

        assertTrue(lib.hasErrors());
        assertTrue(hasCode(lib.diagnostics(), DiagCode.E_MISSING_ARG));
        assertTrue(lib.diagnostics().stream().anyMatch(d -> d.message().contains("amplifier")),
                () -> "expected the missing-param error to name 'amplifier': " + lib.diagnostics());
    }

    @Test
    void verboseUnknownParamIsReported(@TempDir Path root) throws IOException {
        write(root, "enchants/bad.yml", """
            trigger: ATTACK
            levels:
              1: { chance: 100, effects: [ { HEAL: { amount: 2, bogus: 9 } } ] }
            """);
        Library lib = LibraryLoader.load(root, compiler(), 1);

        assertTrue(hasCode(lib.diagnostics(), DiagCode.E_UNKNOWN_EFFECT_PARAM));
    }

    @Test
    void verboseWaitDesugarsToAPrecedingWaitLine(@TempDir Path root) throws IOException {
        write(root, "enchants/delayed.yml", """
            trigger: ATTACK
            levels:
              1: { chance: 100, effects: [ { HEAL: { amount: 4, wait: 20 } } ] }
            """);
        Library lib = LibraryLoader.load(root, compiler(), 1);

        assertFalse(lib.hasErrors(), () -> lib.diagnostics().toString());
        // WAIT is timing, not an emitted effect; it stamps the following effect's delay
        compile.model.CompiledEffect[] effects = lib.snapshot().byStableKey("enchants/delayed/1").effects();
        assertEquals(1, effects.length);
        assertEquals(20, effects[0].cumulativeWaitTicks());
    }

    @Test
    void literalDollarInAMessageSurvives(@TempDir Path root) throws IOException {
        // no scale/token grammar: a '$' in a string is a literal, passes through unchanged
        write(root, "enchants/loot.yml", """
            trigger: ATTACK
            levels:
              1: { chance: 100, effects: [ { MESSAGE: { text: "You earned $500!" } } ] }
            """);
        Library lib = LibraryLoader.load(root, compiler(), 1);

        assertFalse(lib.hasErrors(), () -> lib.diagnostics().toString());
        assertEquals("You earned $500!",
                lib.snapshot().byStableKey("enchants/loot/1").effects()[0].args().str("text"));
    }

    @Test
    void unknownTopLevelKeyWarnsButDoesNotBlock(@TempDir Path root) throws IOException {
        write(root, "enchants/typo2.yml", """
            triggers: ATTACK
            trigger: ATTACK
            levels:
              1: { chance: 100, effects: [{ HEAL: { amount: 2 } }] }
            """);
        Library lib = LibraryLoader.load(root, compiler(), 1);

        assertFalse(lib.hasErrors(), () -> lib.diagnostics().toString());
        assertTrue(hasCode(lib.diagnostics(), DiagCode.W_UNKNOWN_KEY));
    }

    @Test
    void tierFolderSetsTheTierButNotTheStableKey(@TempDir Path root) throws IOException {
        write(root, "enchants/mythic/thunderstrike.yml", """
            trigger: ATTACK
            levels:
              1: { chance: 100, effects: [{ HEAL: { amount: 2 } }] }
            """);
        Library lib = LibraryLoader.load(root, compiler(), 1);

        assertFalse(lib.hasErrors(), () -> lib.diagnostics().toString());
        // key excludes the tier folder, so live gear stamped enchants/thunderstrike still resolves
        assertNotNull(lib.snapshot().byStableKey("enchants/thunderstrike/1"));
        assertNull(lib.snapshot().byStableKey("enchants/mythic/thunderstrike/1"));
        assertEquals("mythic", lib.tierOf("enchants/thunderstrike"));
    }

    @Test
    void inFileTierOverridesTheFolderWithAWarning(@TempDir Path root) throws IOException {
        write(root, "enchants/rare/x.yml", """
            tier: legendary
            trigger: ATTACK
            levels:
              1: { chance: 100, effects: [{ HEAL: { amount: 2 } }] }
            """);
        Library lib = LibraryLoader.load(root, compiler(), 1);

        assertFalse(lib.hasErrors(), () -> lib.diagnostics().toString());
        assertEquals("legendary", lib.tierOf("enchants/x"));
        assertTrue(hasCode(lib.diagnostics(), DiagCode.W_TIER_FOLDER_MISMATCH));
    }

    @Test
    void duplicateKeyAcrossTierFoldersIsAnError(@TempDir Path root) throws IOException {
        String body = "trigger: ATTACK\nlevels:\n  1: { chance: 100, effects: [{ HEAL: { amount: 2 } }] }\n";
        write(root, "enchants/rare/dup.yml", body);
        write(root, "enchants/mythic/dup.yml", body);
        Library lib = LibraryLoader.load(root, compiler(), 1);

        assertTrue(hasCode(lib.diagnostics(), DiagCode.E_DUPLICATE_KEY));
    }

    @Test
    void tiersFileDrivesTheRegistry(@TempDir Path root) throws IOException {
        write(root, "tiers.yml", """
            default-tier: scrap
            tiers:
              scrap: { color: "&8", weight: 1, glint: false }
              divine: { color: "&d&l", weight: 99, glint: true }
            """);
        write(root, "enchants/divine/x.yml", "trigger: ATTACK\nlevels:\n  1: { chance: 100, effects: [{ HEAL: { amount: 2 } }] }\n");
        write(root, "enchants/plain.yml", "trigger: ATTACK\nlevels:\n  1: { chance: 100, effects: [{ HEAL: { amount: 2 } }] }\n");
        Library lib = LibraryLoader.load(root, compiler(), 1);

        assertFalse(lib.hasErrors(), () -> lib.diagnostics().toString());
        assertEquals("divine", lib.tierOf("enchants/x"));
        assertEquals("scrap", lib.tierOf("enchants/plain"));
        assertNotNull(lib.tiers().tier("divine"));
    }

    @Test
    void descriptionListJoinsWithNewlines(@TempDir Path root) throws IOException {
        write(root, "enchants/lore.yml", """
            trigger: ATTACK
            description:
              - "Line one."
              - "Line two."
            levels:
              1: { chance: 100, effects: [{ HEAL: { amount: 2 } }] }
            """);
        Library lib = LibraryLoader.load(root, compiler(), 1);

        assertFalse(lib.hasErrors(), () -> lib.diagnostics().toString());
        assertEquals("Line one.\nLine two.", lib.catalog().get(0).description());
    }
}
