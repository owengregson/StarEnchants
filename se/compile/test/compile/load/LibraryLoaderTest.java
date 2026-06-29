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
import schema.diag.DiagCode;
import schema.spec.D;
import schema.spec.ParamSpec;

/** Unit tests for the directory loader (ADR-0014), through a real {@link Compiler} — no server. */
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

    /** Assert a diagnostic with the given code was emitted — the contract, not just "the load failed". */
    private static void assertCode(Library lib, DiagCode code) {
        assertTrue(lib.diagnostics().stream().anyMatch(d -> d.is(code)), () -> lib.diagnostics().toString());
    }

    @Test
    void loadsADirectoryTreeIntoASnapshotAndCatalog(@TempDir Path root) throws IOException {
        write(root, "enchants/lifesteal.yml", """
            display: "&cLifesteal"
            trigger: ATTACK
            levels:
              1: { chance: 10, effects: [{ HEAL: { amount: 2 } }] }
              2: { chance: 20, effects: [{ HEAL: { amount: 4 } }] }
            """);
        write(root, "enchants/herald.yml", """
            display: "Herald"
            trigger: ATTACK
            levels:
              1: { chance: 100, effects: [{ MESSAGE: { text: hi } }] }
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
              1: { chance: 100, effects: [{ HEAL: { amount: 2 } }] }
            """);
        write(root, "crystals/jolt.yml", """
            display: "&bJolt"
            description: "a zap on hit"
            trigger: ATTACK
            chance: 100
            effects: [{ MESSAGE: { text: zap } }]
            """);

        Library lib = LibraryLoader.load(root, compiler(), 7);

        assertFalse(lib.hasErrors(), () -> lib.diagnostics().toString());
        // crystal key is the base key, no /level suffix — WornResolver looks it up directly
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
    void loadsSetsAsArmourAndWeaponAbilitiesCarryingTheCompletionThreshold(@TempDir Path root) throws IOException {
        write(root, "sets/yeti.yml", """
            display: "&bYeti"
            complete: 4
            armor:
              pieces:
                helmet:     { material: DIAMOND_HELMET,     name: "&bYeti Helm" }
                chestplate: { material: DIAMOND_CHESTPLATE, name: "&bYeti Chestplate" }
                leggings:   { material: DIAMOND_LEGGINGS,   name: "&bYeti Leggings" }
                boots:      { material: DIAMOND_BOOTS,      name: "&bYeti Boots" }
              trigger: DEFENSE
              effects: [{ HEAL: { amount: 1 } }]
            weapon:
              material: DIAMOND_SWORD
              name: "&bYeti Blade"
              trigger: ATTACK
              effects: [{ HEAL: { amount: 1 } }]
            """);

        Library lib = LibraryLoader.load(root, compiler(), 8);

        assertFalse(lib.hasErrors(), () -> lib.diagnostics().toString());
        compile.model.Ability bonus = lib.snapshot().byStableKey("sets/yeti");
        assertNotNull(bonus);
        assertEquals(compile.model.SourceKind.SET, bonus.sourceKind());
        assertEquals(4, bonus.setPieces()); // completion threshold erased onto the armour ability
        // the weapon bonus is its own ability, resolver-gated → setPieces 0
        compile.model.Ability weapon = lib.snapshot().byStableKey("sets/yeti/weapon");
        assertNotNull(weapon);
        assertEquals(compile.model.SourceKind.SET, weapon.sourceKind());
        assertEquals(0, weapon.setPieces());
        assertEquals(1, lib.sets().size());
        assertEquals("sets/yeti", lib.sets().get(0).key());
        assertEquals(4, lib.sets().get(0).armorComplete());
        assertTrue(lib.sets().get(0).hasWeapon());
        assertEquals("&bYeti", lib.displayNameOf("sets/yeti"));
    }

    @Test
    void aSetWithNoArmourPiecesIsABlockingError(@TempDir Path root) throws IOException {
        write(root, "sets/broken.yml", """
            display: "Broken"
            """);
        Library lib = LibraryLoader.load(root, compiler(), 9);
        assertCode(lib, DiagCode.E_LOAD_SET_ARMOR);
    }

    @Test
    void aMalformedFileIsReportedButValidContentStillLoads(@TempDir Path root) throws IOException {
        write(root, "enchants/good.yml", """
            trigger: ATTACK
            levels:
              1: { chance: 50, effects: [{ HEAL: { amount: 2 } }] }
            """);
        write(root, "enchants/bad.yml", "trigger: ATTACK\n");

        Library lib = LibraryLoader.load(root, compiler(), 2);

        assertCode(lib, DiagCode.E_LOAD_ENCHANT_LEVELS); // the bad file (no levels:) is reported by code...
        assertNotNull(lib.snapshot().byStableKey("enchants/good/1")); // ...and the good sibling still loads
    }

    @Test
    void aFileWithNoNameStemIsReportedNotKeyedEmpty(@TempDir Path root) throws IOException {
        // a file named just ".yml" yields a degenerate empty base key — must be rejected
        write(root, "enchants/.yml", """
            trigger: ATTACK
            levels:
              1: { chance: 50, effects: [{ HEAL: { amount: 2 } }] }
            """);
        Library lib = LibraryLoader.load(root, compiler(), 4);
        assertCode(lib, DiagCode.E_LOAD_KEY);
        assertEquals(0, lib.snapshot().abilityCount());
    }

    @Test
    void requiresAnUnknownEnchantIsABlockingError(@TempDir Path root) throws IOException {
        write(root, "enchants/upgrade.yml", """
            display: "Upgrade"
            trigger: ATTACK
            requires: ["enchants/ghost"]
            levels:
              1: { chance: 100, effects: [{ HEAL: { amount: 1 } }] }
            """);
        Library lib = LibraryLoader.load(root, compiler(), 1);
        assertCode(lib, DiagCode.E_REL_UNKNOWN); // a requires: naming a non-existent enchant
    }

    @Test
    void blacklistsAnUnknownEnchantIsABlockingError(@TempDir Path root) throws IOException {
        write(root, "enchants/rival.yml", """
            display: "Rival"
            trigger: ATTACK
            blacklist: ["enchants/phantom"]
            levels:
              1: { chance: 100, effects: [{ HEAL: { amount: 1 } }] }
            """);
        Library lib = LibraryLoader.load(root, compiler(), 1);
        assertCode(lib, DiagCode.E_REL_UNKNOWN);
    }

    @Test
    void validRequiresAndBlacklistBetweenExistingEnchantsCompileClean(@TempDir Path root) throws IOException {
        write(root, "enchants/base.yml", """
            display: "Base"
            trigger: ATTACK
            levels:
              1: { chance: 100, effects: [{ HEAL: { amount: 1 } }] }
            """);
        write(root, "enchants/upgrade.yml", """
            display: "Upgrade"
            trigger: ATTACK
            requires: ["enchants/base"]
            blacklist: ["enchants/base"]
            levels:
              1: { chance: 100, effects: [{ HEAL: { amount: 2 } }] }
            """);
        Library lib = LibraryLoader.load(root, compiler(), 1);
        assertFalse(lib.hasErrors(), () -> lib.diagnostics().toString());
    }

    @Test
    void aSetReferencingAnUnknownCustomEnchantIsABlockingError(@TempDir Path root) throws IOException {
        // §6.6: a custom enchants/<id> ref with no matching enchant would silently mint a piece missing
        // its enchant — the whole-library check turns the typo into a blocking diagnostic instead.
        write(root, "sets/frostguard.yml", """
            display: "Frostguard"
            complete: 1
            armor:
              enchants:
                enchants/ghost: 1
              pieces:
                boots: { material: DIAMOND_BOOTS, name: "Boots" }
              trigger: DEFENSE
              effects: [{ HEAL: { amount: 1 } }]
            """);
        Library lib = LibraryLoader.load(root, compiler(), 1);
        assertCode(lib, DiagCode.E_SET_ENCHANT_UNKNOWN);
    }

    @Test
    void aSetReferencingACustomEnchantAtAnOutOfRangeLevelIsABlockingError(@TempDir Path root) throws IOException {
        write(root, "enchants/frost.yml", """
            display: "Frost"
            trigger: ATTACK
            levels:
              1: { chance: 100, effects: [{ HEAL: { amount: 1 } }] }
              2: { chance: 100, effects: [{ HEAL: { amount: 2 } }] }
            """);
        write(root, "sets/frostguard.yml", """
            display: "Frostguard"
            complete: 1
            armor:
              enchants:
                enchants/frost: 5
              pieces:
                boots: { material: DIAMOND_BOOTS, name: "Boots" }
              trigger: DEFENSE
              effects: [{ HEAL: { amount: 1 } }]
            """);
        Library lib = LibraryLoader.load(root, compiler(), 1);
        // enchants/frost tops out at level 2; a level-5 ref is out of range.
        assertCode(lib, DiagCode.E_SET_ENCHANT_LEVEL);
    }

    @Test
    void aSetWithAValidCustomRefAndAVanillaNameCompilesClean(@TempDir Path root) throws IOException {
        write(root, "enchants/frost.yml", """
            display: "Frost"
            trigger: ATTACK
            levels:
              1: { chance: 100, effects: [{ HEAL: { amount: 1 } }] }
              2: { chance: 100, effects: [{ HEAL: { amount: 2 } }] }
            """);
        write(root, "sets/frostguard.yml", """
            display: "Frostguard"
            complete: 1
            armor:
              enchants:
                enchants/frost: 2
                SHARPNESS: 5
              pieces:
                boots: { material: DIAMOND_BOOTS, name: "Boots" }
              trigger: DEFENSE
              effects: [{ HEAL: { amount: 1 } }]
            """);
        Library lib = LibraryLoader.load(root, compiler(), 1);
        // enchants/frost at level 2 is in range; SHARPNESS is a vanilla NAME (no enchants/ prefix) resolved
        // cross-version at mint, so it is skipped here — its "5" must NOT be range-checked against a custom max.
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
