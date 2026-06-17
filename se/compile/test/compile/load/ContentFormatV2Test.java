package compile.load;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.Compiler;
import compile.MapSpecRegistry;
import compile.model.Ability;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import schema.diag.Diagnostic;
import schema.spec.D;
import schema.spec.ParamSpec;

/**
 * Unit tests for content format v2 (ADR-0016): verbose effects, level scaling, {@code effects+}
 * overrides, tier subfolders with key stability, duplicate-key detection, carrier {@code ItemDef}s,
 * and list descriptions. Uses a real {@link Compiler} wired with a tiny effect-spec registry (HEAL,
 * MESSAGE, POTION) — no server — and the real {@link LibraryLoader}, so the verbose lowering and scale
 * substitution are exercised end to end through the production compile path.
 */
class ContentFormatV2Test {

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

    private static boolean hasCode(List<Diagnostic> diags, String code) {
        return diags.stream().anyMatch(d -> d.code().equals(code));
    }

    // ── verbose effects ───────────────────────────────────────────────────────────────────────────

    @Test
    void verboseEffectLowersToTheSameArgsAsTerse(@TempDir Path root) throws IOException {
        write(root, "enchants/terse.yml", """
            trigger: ATTACK
            levels:
              1: { chance: 100, effects: ["POTION:STRENGTH:1:100"] }
            """);
        write(root, "enchants/verbose.yml", """
            trigger: ATTACK
            levels:
              1: { chance: 100, effects: [ { POTION: { effect: STRENGTH, amplifier: 1, duration: 100 } } ] }
            """);
        Library lib = LibraryLoader.load(root, compiler(), 1);

        assertFalse(lib.hasErrors(), () -> lib.diagnostics().toString());
        var terse = lib.snapshot().byStableKey("enchants/terse/1").effects()[0].args();
        var verbose = lib.snapshot().byStableKey("enchants/verbose/1").effects()[0].args();
        assertEquals("STRENGTH", verbose.str("effect"));
        assertEquals(1, verbose.integer("amplifier"));
        assertEquals(100, verbose.integer("duration"));
        assertEquals(terse.asMap(), verbose.asMap()); // byte-identical typed args
    }

    @Test
    void verboseStringArgWithAColonIsNotShredded(@TempDir Path root) throws IOException {
        // The B2 fix: a verbose string value is placed as one whole arg — a ':' inside it must survive.
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
        // The M5 fix: a value starting with '@' is the text, never peeled as a target selector.
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
        assertTrue(hasCode(lib.diagnostics(), "E_MISSING_ARG"));
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

        assertTrue(hasCode(lib.diagnostics(), "E_UNKNOWN_EFFECT_PARAM"));
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
        // WAIT is a timing directive (not an emitted effect); it stamps the following effect's delay.
        compile.model.CompiledEffect[] effects = lib.snapshot().byStableKey("enchants/delayed/1").effects();
        assertEquals(1, effects.length);
        assertEquals(20, effects[0].cumulativeWaitTicks());
    }

    // ── scaling ───────────────────────────────────────────────────────────────────────────────────

    @Test
    void scaleLevelMapFillsPerLevelValuesFromOneEffectLine(@TempDir Path root) throws IOException {
        write(root, "enchants/thunder.yml", """
            trigger: ATTACK
            max-level: 3
            scale:
              heal: { 1: 2, 2: 4, 3: 6 }
            effects:
              - { HEAL: { amount: $heal } }
            """);
        Library lib = LibraryLoader.load(root, compiler(), 1);

        assertFalse(lib.hasErrors(), () -> lib.diagnostics().toString());
        assertEquals(2.0, healAmount(lib, "enchants/thunder/1"), 1e-9);
        assertEquals(4.0, healAmount(lib, "enchants/thunder/2"), 1e-9);
        assertEquals(6.0, healAmount(lib, "enchants/thunder/3"), 1e-9);
    }

    @Test
    void scaleLinearFromStepIsIntegerExact(@TempDir Path root) throws IOException {
        write(root, "enchants/ramp.yml", """
            trigger: ATTACK
            max-level: 3
            scale:
              power: { from: 10, step: 5 }
            chance: $power
            effects:
              - { HEAL: { amount: 1 } }
            """);
        Library lib = LibraryLoader.load(root, compiler(), 1);

        assertFalse(lib.hasErrors(), () -> lib.diagnostics().toString());
        assertEquals(10.0, lib.snapshot().byStableKey("enchants/ramp/1").baseChance(), 1e-9);
        assertEquals(15.0, lib.snapshot().byStableKey("enchants/ramp/2").baseChance(), 1e-9);
        assertEquals(20.0, lib.snapshot().byStableKey("enchants/ramp/3").baseChance(), 1e-9);
    }

    @Test
    void conditionScalesAsAPerLevelMap(@TempDir Path root) throws IOException {
        write(root, "enchants/gated.yml", """
            trigger: DEFENSE
            max-level: 3
            condition: { 1: "%actor.health% <= 4", 2: "%actor.health% <= 5", 3: "%actor.health% <= 6" }
            effects:
              - { HEAL: { amount: 2 } }
            """);
        Library lib = LibraryLoader.load(root, compiler(), 1);

        assertFalse(lib.hasErrors(), () -> lib.diagnostics().toString());
        // Every level must get a (non-null) condition — i.e. the per-level condition MAP was read, not
        // silently dropped (the bug this guards). Per-level VALUE correctness is proven end-to-end by the
        // catalog equivalence gate (CatalogEquivalenceTest, e.g. guardian-angel's scaling health gate).
        org.junit.jupiter.api.Assertions.assertNotNull(
                lib.snapshot().byStableKey("enchants/gated/1").condition(), "level 1 condition");
        org.junit.jupiter.api.Assertions.assertNotNull(
                lib.snapshot().byStableKey("enchants/gated/3").condition(), "level 3 condition");
    }

    @Test
    void levelEffectsPlusAppendsToTheTemplatedList(@TempDir Path root) throws IOException {
        write(root, "enchants/capstone.yml", """
            trigger: ATTACK
            max-level: 3
            effects:
              - { HEAL: { amount: 2 } }
            levels:
              3:
                effects+:
                  - { MESSAGE: { text: "capstone!" } }
            """);
        Library lib = LibraryLoader.load(root, compiler(), 1);

        assertFalse(lib.hasErrors(), () -> lib.diagnostics().toString());
        assertEquals(1, lib.snapshot().byStableKey("enchants/capstone/1").effects().length);
        compile.model.CompiledEffect[] l3 = lib.snapshot().byStableKey("enchants/capstone/3").effects();
        assertEquals(2, l3.length);
        assertEquals("MESSAGE", l3[1].head());
    }

    @Test
    void levelMapValueMayReferenceAScaleToken(@TempDir Path root) throws IOException {
        // A knob written as a per-level map may itself reference a $token (substituted at that level).
        write(root, "enchants/tok.yml", """
            trigger: ATTACK
            max-level: 2
            scale:
              big: { 1: 100, 2: 50 }
            cooldown: { 1: $big, 2: 10 }
            effects:
              - { HEAL: { amount: 1 } }
            """);
        Library lib = LibraryLoader.load(root, compiler(), 1);

        assertFalse(lib.hasErrors(), () -> lib.diagnostics().toString());
        assertEquals(100, lib.snapshot().byStableKey("enchants/tok/1").cooldownTicks());
        assertEquals(10, lib.snapshot().byStableKey("enchants/tok/2").cooldownTicks());
    }

    @Test
    void unknownScaleTokenIsReportedWhenAScaleBlockIsDeclared(@TempDir Path root) throws IOException {
        write(root, "enchants/typo.yml", """
            trigger: ATTACK
            max-level: 1
            scale:
              real: { 1: 5 }
            effects:
              - { HEAL: { amount: $nope } }
            """);
        Library lib = LibraryLoader.load(root, compiler(), 1);
        assertTrue(hasCode(lib.diagnostics(), "E_SCALE"));
    }

    @Test
    void literalDollarSurvivesWhenNoScaleBlock(@TempDir Path root) throws IOException {
        // v1 compat: a '$' in a message (a money amount, another plugin's token) must NOT error.
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
    void escapedDollarBecomesALiteralDollar(@TempDir Path root) throws IOException {
        write(root, "enchants/esc.yml", """
            trigger: ATTACK
            max-level: 1
            scale:
              n: { 1: 7 }
            effects:
              - { MESSAGE: { text: "price: $$$n" } }
            """);
        Library lib = LibraryLoader.load(root, compiler(), 1);

        assertFalse(lib.hasErrors(), () -> lib.diagnostics().toString());
        // $$ → a literal $, then $n → 7
        assertEquals("price: $7", lib.snapshot().byStableKey("enchants/esc/1").effects()[0].args().str("text"));
    }

    @Test
    void unknownTopLevelKeyWarnsButDoesNotBlock(@TempDir Path root) throws IOException {
        write(root, "enchants/typo2.yml", """
            triggers: ATTACK
            trigger: ATTACK
            levels:
              1: { chance: 100, effects: ["HEAL:2"] }
            """);
        Library lib = LibraryLoader.load(root, compiler(), 1);

        assertFalse(lib.hasErrors(), () -> lib.diagnostics().toString()); // a warning, never blocking
        assertTrue(hasCode(lib.diagnostics(), "W_UNKNOWN_KEY"));
    }

    // ── tiers, key stability, duplicate keys, items, description ─────────────────────────────────────

    @Test
    void tierFolderSetsTheTierButNotTheStableKey(@TempDir Path root) throws IOException {
        write(root, "enchants/mythic/thunderstrike.yml", """
            trigger: ATTACK
            levels:
              1: { chance: 100, effects: ["HEAL:2"] }
            """);
        Library lib = LibraryLoader.load(root, compiler(), 1);

        assertFalse(lib.hasErrors(), () -> lib.diagnostics().toString());
        // Key excludes the tier folder — so live gear stamped enchants/thunderstrike still resolves.
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
              1: { chance: 100, effects: ["HEAL:2"] }
            """);
        Library lib = LibraryLoader.load(root, compiler(), 1);

        assertFalse(lib.hasErrors(), () -> lib.diagnostics().toString());
        assertEquals("legendary", lib.tierOf("enchants/x"));
        assertTrue(hasCode(lib.diagnostics(), "W_TIER_FOLDER_MISMATCH"));
    }

    @Test
    void duplicateKeyAcrossTierFoldersIsAnError(@TempDir Path root) throws IOException {
        String body = "trigger: ATTACK\nlevels:\n  1: { chance: 100, effects: [\"HEAL:2\"] }\n";
        write(root, "enchants/rare/dup.yml", body);
        write(root, "enchants/mythic/dup.yml", body);
        Library lib = LibraryLoader.load(root, compiler(), 1);

        assertTrue(hasCode(lib.diagnostics(), "E_DUPLICATE_KEY"));
    }

    @Test
    void tiersFileDrivesTheRegistry(@TempDir Path root) throws IOException {
        write(root, "tiers.yml", """
            default-tier: scrap
            tiers:
              scrap: { color: "&8", weight: 1, glint: false }
              divine: { color: "&d&l", weight: 99, glint: true }
            """);
        write(root, "enchants/divine/x.yml", "trigger: ATTACK\nlevels:\n  1: { chance: 100, effects: [\"HEAL:2\"] }\n");
        write(root, "enchants/plain.yml", "trigger: ATTACK\nlevels:\n  1: { chance: 100, effects: [\"HEAL:2\"] }\n");
        Library lib = LibraryLoader.load(root, compiler(), 1);

        assertFalse(lib.hasErrors(), () -> lib.diagnostics().toString());
        assertEquals("divine", lib.tierOf("enchants/x"));
        assertEquals("scrap", lib.tierOf("enchants/plain")); // the declared default
        assertNotNull(lib.tiers().tier("divine"));
    }

    @Test
    void carrierItemLoadsAsAZeroAbilityItemDef(@TempDir Path root) throws IOException {
        write(root, "items/book/thunder-book.yml", """
            display: "&dThunder Book"
            description: "Apply Thunderstrike."
            kind: book
            grants:
              enchant: enchants/thunderstrike
              level: 3
            apply:
              success-chance: 75
              destroy-on-fail: true
            """);
        Library lib = LibraryLoader.load(root, compiler(), 1);

        assertFalse(lib.hasErrors(), () -> lib.diagnostics().toString());
        assertEquals(0, lib.snapshot().abilityCount()); // carriers never compile to abilities
        assertEquals(1, lib.items().size());
        ItemDef book = lib.items().get(0);
        assertEquals("items/book/thunder-book", book.key());
        assertEquals("book", book.kind());
        assertEquals("ENCHANTED_BOOK", book.material()); // per-kind default
        assertEquals("enchants/thunderstrike", book.grant().enchant());
        assertEquals(3, book.grant().level());
        assertEquals(75, book.apply().successChance());
        assertTrue(book.apply().destroyOnFail());
    }

    @Test
    void descriptionListJoinsWithNewlines(@TempDir Path root) throws IOException {
        write(root, "enchants/lore.yml", """
            trigger: ATTACK
            description:
              - "Line one."
              - "Line two."
            levels:
              1: { chance: 100, effects: ["HEAL:2"] }
            """);
        Library lib = LibraryLoader.load(root, compiler(), 1);

        assertFalse(lib.hasErrors(), () -> lib.diagnostics().toString());
        assertEquals("Line one.\nLine two.", lib.catalog().get(0).description());
    }

    private static double healAmount(Library lib, String key) {
        Ability ability = lib.snapshot().byStableKey(key);
        return ability.effects()[0].args().dbl("amount");
    }
}
