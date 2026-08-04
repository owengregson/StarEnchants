package compile.load;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.def.AbilityDef;
import compile.model.SourceKind;
import java.util.List;
import java.util.function.IntSupplier;
import org.junit.jupiter.api.Test;
import schema.diag.DiagCode;
import schema.diag.Diagnostics;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Unit tests for the pet reader (ADR-0052): malformed input is a diagnostic, never an exception. */
class PetDefReaderTest {

    private static YamlNode root(String yaml, Diagnostics diags) {
        return YamlNode.compose("test.yml", yaml, diags);
    }

    private static IntSupplier counter() {
        int[] id = {0};
        return () -> id[0]++;
    }

    private static void assertCode(Diagnostics diags, DiagCode code) {
        assertTrue(diags.all().stream().anyMatch(d -> d.is(code)), () -> diags.all().toString());
    }

    @Test
    void activePetSplitsUseAndWornAbilitiesByTrigger() {
        Diagnostics diags = new Diagnostics();
        String yaml = """
            display: "Shield"
            color: "&7"
            type: ACTIVE
            head: "aGVhZA=="
            description: [ "&7&l* Reduces incoming damage" ]
            levels:
              1:
                cooldown: 1800
                duration: 60
                abilities:
                  - { effects: [ { SOUND: { sound: BLOCK_ANVIL_PLACE } } ] }
                  - { trigger: DEFENSE, effects: [ { DAMAGE_MOD: { side: defense, mode: add, amount: 15 } } ] }
            """;
        PetDefReader.Parsed parsed = PetDefReader.read("shield", root(yaml, diags), counter(), diags);

        assertFalse(diags.hasErrors(), () -> diags.all().toString());
        PetDef def = parsed.def();
        assertTrue(def.active());
        assertEquals("&7", def.color());
        assertEquals("aGVhZA==", def.head());
        PetBracket bracket = def.brackets().get(0);
        assertEquals(1, bracket.floor());
        assertEquals(1800, bracket.cooldownTicks());
        assertEquals(60, bracket.durationTicks());
        assertEquals(List.of("pet:shield/1"), bracket.useStableKeys());
        assertEquals(List.of("pet:shield/1/a1"), bracket.wornStableKeys());

        AbilityDef use = parsed.abilities().get(0);
        assertEquals(SourceKind.PET, use.sourceKind());
        assertEquals(List.of("USE"), use.triggers());
        assertEquals(1800, use.cooldownTicks()); // inherited from the bracket
        assertEquals("pet:shield", use.cdScopeEnchant()); // pet-wide: a bracket crossing keeps the cooldown
        assertEquals("pet:shield", use.suppressKey());
        AbilityDef worn = parsed.abilities().get(1);
        assertEquals(List.of("DEFENSE"), worn.triggers());
        assertEquals(0, worn.cooldownTicks()); // worn riders never inherit the right-click cooldown
        assertEquals("pet:shield/1/a1", worn.cdScopeEnchant()); // own scope, never the pet-wide one
    }

    @Test
    void passivePetDefaultsToPassiveAndForcesAuthoredUseBack() {
        Diagnostics diags = new Diagnostics();
        String yaml = """
            display: "Silverfish"
            type: PASSIVE
            levels:
              1: { effects: [ { POTION: { effect: FAST_DIGGING, level: 1, duration: 300, who: "@Self" } } ] }
              25:
                abilities:
                  - { trigger: USE, effects: [ { POTION: { effect: FAST_DIGGING, level: 2, duration: 300, who: "@Self" } } ] }
            """;
        PetDefReader.Parsed parsed = PetDefReader.read("silverfish", root(yaml, diags), counter(), diags);

        assertFalse(diags.hasErrors(), () -> diags.all().toString());
        assertCode(diags, DiagCode.W_LOAD_PET_TRIGGER); // USE on a passive: warned + forced back
        assertEquals(List.of("PASSIVE"), parsed.abilities().get(0).triggers());
        assertEquals(List.of("PASSIVE"), parsed.abilities().get(1).triggers());
        PetDef def = parsed.def();
        assertTrue(def.brackets().stream().allMatch(b -> b.useStableKeys().isEmpty()));
        assertEquals("&f", def.color()); // colour defaults white
        assertFalse(def.active());
    }

    @Test
    void messageOnNoHomeParsesAndDefaultsEmpty() {
        Diagnostics diags = new Diagnostics();
        String yaml = """
            display: "Mole"
            type: ACTIVE
            message-on-no-home: "&cNo home yet!"
            levels:
              1: { cooldown: 100, condition: "%sneaking%", effects: [ { DIG_HOME: { window: 600, range: 50 } } ] }
            """;
        PetDefReader.Parsed parsed = PetDefReader.read("mole", root(yaml, diags), counter(), diags);
        assertFalse(diags.hasErrors(), () -> diags.all().toString()); // the new ROOT_KEYS entry: no unknown-key warning
        assertEquals("&cNo home yet!", parsed.def().messageOnNoHome());

        Diagnostics bare = new Diagnostics();
        String without = """
            display: "Mole"
            type: ACTIVE
            levels:
              1: { cooldown: 100, condition: "%sneaking%", effects: [ { DIG_HOME: { window: 600, range: 50 } } ] }
            """;
        assertEquals("", PetDefReader.read("mole", root(without, bare), counter(), bare).def().messageOnNoHome());
    }

    /** Both ladder keys are READ by the reader, so they must also be KNOWN to it — no unknown-key warning. */
    @Test
    void expCurveAndMaxLevelAreAcceptedWarningFree() {
        Diagnostics diags = new Diagnostics();
        String yaml = """
            display: "Lava Elemental Pet"
            type: ACTIVE
            exp-curve: { base: 250, per-level: 1000 }
            max-level: 10
            levels:
              1: { cooldown: 100, effects: [ { SOUND: { sound: BLOCK_LAVA_POP } } ] }
            """;
        PetDefReader.Parsed parsed = PetDefReader.read("lava", root(yaml, diags), counter(), diags);

        assertTrue(diags.all().stream().noneMatch(d -> d.is(DiagCode.W_UNKNOWN_KEY)), () -> diags.all().toString());
        assertEquals(new PetCurve(250, 1000), parsed.def().expCurve());
        assertEquals(10, parsed.def().maxLevel());
    }

    @Test
    void bracketsSortAscendingRegardlessOfAuthoredOrder() {
        Diagnostics diags = new Diagnostics();
        String yaml = """
            display: "Chicken"
            type: PASSIVE
            levels:
              100: { effects: [ { POTION: { effect: SPEED, level: 3, duration: 300, who: "@Self" } } ] }
              1:   { effects: [ { POTION: { effect: SPEED, level: 1, duration: 300, who: "@Self" } } ] }
              25:  { effects: [ { POTION: { effect: SPEED, level: 2, duration: 300, who: "@Self" } } ] }
            """;
        PetDefReader.Parsed parsed = PetDefReader.read("chicken", root(yaml, diags), counter(), diags);
        assertFalse(diags.hasErrors());
        assertEquals(List.of(1, 25, 100), parsed.def().brackets().stream().map(PetBracket::floor).toList());
    }

    /** The sparse-floor rule: the highest authored floor at or below the level is live (25 spans 25-99 here). */
    @ParameterizedTest
    @CsvSource({"0,1", "1,1", "24,1", "25,25", "99,25", "100,100", "250,100"})
    void bracketForPicksTheHighestFloorAtOrBelow(int level, int expectedFloor) {
        Diagnostics diags = new Diagnostics();
        String yaml = """
            display: "Chicken"
            type: PASSIVE
            levels:
              1:   { effects: [ { POTION: { effect: SPEED, level: 1, duration: 300, who: "@Self" } } ] }
              25:  { effects: [ { POTION: { effect: SPEED, level: 2, duration: 300, who: "@Self" } } ] }
              100: { effects: [ { POTION: { effect: SPEED, level: 3, duration: 300, who: "@Self" } } ] }
            """;
        PetDef def = PetDefReader.read("chicken", root(yaml, diags), counter(), diags).def();
        assertEquals(expectedFloor, def.bracketFor(level).floor());
    }

    @Test
    void missingDisplayOrTypeOrLevelsIsAnErrorNotAThrow() {
        Diagnostics diags = new Diagnostics();
        assertNull(PetDefReader.read("bad", root("display: X", diags), counter(), diags).def());
        assertCode(diags, DiagCode.E_LOAD_PET);

        Diagnostics diags2 = new Diagnostics();
        assertNull(PetDefReader.read("bad", root("display: X\ntype: WEIRD\nlevels: { 1: { effects: [] } }", diags2),
                counter(), diags2).def());
        assertCode(diags2, DiagCode.E_LOAD_PET);

        Diagnostics diags3 = new Diagnostics();
        assertNull(PetDefReader.read("bad", root("display: X\ntype: ACTIVE", diags3), counter(), diags3).def());
        assertCode(diags3, DiagCode.E_LOAD_PET);
    }

    @Test
    void nonNumericFloorIsSkippedWithAnErrorAndTheRestSurvive() {
        Diagnostics diags = new Diagnostics();
        String yaml = """
            display: "Cow"
            type: ACTIVE
            levels:
              max: { cooldown: 10, effects: [ { CURE: { category: HARMFUL, who: "@Self" } } ] }
              1:   { cooldown: 20, effects: [ { CURE: { category: HARMFUL, who: "@Self" } } ] }
            """;
        PetDefReader.Parsed parsed = PetDefReader.read("cow", root(yaml, diags), counter(), diags);
        assertCode(diags, DiagCode.E_LOAD_PET);
        assertEquals(1, parsed.def().brackets().size());
    }

    @Test
    void repeatingAbilityCarriesItsPeriod() {
        Diagnostics diags = new Diagnostics();
        String yaml = """
            display: "Fish"
            type: PASSIVE
            levels:
              1: { trigger: REPEATING, repeat: 72000, effects: [ { MODIFY_MONEY: { mode: interest_percent, amount: 0.7, who: "@Self" } } ] }
            """;
        PetDefReader.Parsed parsed = PetDefReader.read("fish", root(yaml, diags), counter(), diags);
        assertFalse(diags.hasErrors(), () -> diags.all().toString());
        AbilityDef ability = parsed.abilities().get(0);
        assertEquals(List.of("REPEATING"), ability.triggers());
        assertEquals(72000, ability.repeatTicks());
    }
}
