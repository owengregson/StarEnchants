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

/** Unit tests for the reforge reader (ADR-0070): the a0 active is implicit-USE; malformed input is a diagnostic. */
class ReforgeDefReaderTest {

    private static YamlNode root(String yaml, Diagnostics diags) {
        return YamlNode.compose("test.yml", yaml, diags);
    }

    private static IntSupplier counter() {
        int[] id = {0};
        return () -> id[0]++;
    }

    /** Assert a diagnostic with the given code was emitted — the contract, not just "something failed". */
    private static void assertCode(Diagnostics diags, DiagCode code) {
        assertTrue(diags.all().stream().anyMatch(d -> d.is(code)), () -> diags.all().toString());
    }

    @Test
    void readsIdentityAndAbilities() {
        Diagnostics diags = new Diagnostics();
        String yaml = """
            display: "Testforge"
            color: "&5"
            icon: SUGAR
            description:
              - "&5&lTESTFORGE"
              - "&5* Does a thing."
            abilities:
              - cooldown: 200
                chance: 80
                condition: "%actor.health% > 4"
                effects: [{ POTION: { effect: SPEED, level: 2, duration: 200, who: "@Self" } }]
              - trigger: PASSIVE
                effects: [{ POTION: { effect: REGENERATION, level: 1, duration: 200, who: "@Self" } }]
            """;
        ReforgeDefReader.Parsed parsed = ReforgeDefReader.read("reforges/x", root(yaml, diags), counter(), diags);

        assertFalse(diags.hasErrors(), () -> diags.all().toString());
        ReforgeDef def = parsed.def();
        assertEquals("reforges/x", def.key());
        assertEquals("Testforge", def.display());
        assertEquals("&5", def.color());
        assertEquals("SUGAR", def.icon());
        assertEquals(List.of("&5&lTESTFORGE", "&5* Does a thing."), def.description());
        // {TIME_FORMATTED} renders a0's cooldown; only USE abilities feed useStableKeys/conditionSources.
        assertEquals(200, def.cooldownTicks());
        assertEquals(List.of("reforges/x"), def.useStableKeys());          // a1 PASSIVE is excluded
        assertEquals(List.of("%actor.health% > 4"), def.conditionSources());

        assertEquals(2, parsed.abilities().size());
        AbilityDef a0 = parsed.abilities().get(0);
        assertEquals(SourceKind.REFORGE, a0.sourceKind());
        assertEquals("reforges/x", a0.stableKey());
        assertEquals(List.of("USE"), a0.triggers());       // the active fires on the implicit USE trigger
        assertEquals(80.0, a0.baseChance(), 1e-9);
        assertEquals(200, a0.cooldownTicks());
        assertEquals("reforges/x", a0.cdScopeEnchant());   // ENCHANT-scope cooldown on its own stable key
        assertEquals("reforges/x", a0.suppressKey());

        AbilityDef a1 = parsed.abilities().get(1);
        assertEquals("reforges/x/a1", a1.stableKey());
        assertEquals(List.of("PASSIVE"), a1.triggers());   // a support ability's authored trigger passes through
        assertEquals(SourceKind.REFORGE, a1.sourceKind());
    }

    @Test
    void activeTriggerIsForcedToUseWithWarning() {
        Diagnostics diags = new Diagnostics();
        String yaml = """
            display: "X"
            trigger: ATTACK
            effects: [{ POTION: { effect: SPEED, level: 1, duration: 100, who: "@Self" } }]
            """;
        ReforgeDefReader.Parsed parsed = ReforgeDefReader.read("reforges/x", root(yaml, diags), counter(), diags);
        assertCode(diags, DiagCode.W_LOAD_REFORGE_TRIGGER);                 // the authored ATTACK is warned...
        assertEquals(List.of("USE"), parsed.abilities().get(0).triggers()); // ...and forced back to USE
    }

    @Test
    void supportWithNoTriggerDefaultsToUse() {
        Diagnostics diags = new Diagnostics();
        String yaml = """
            display: "X"
            abilities:
              - effects: [{ POTION: { effect: SPEED, level: 1, duration: 100, who: "@Self" } }]
              - effects: [{ POTION: { effect: REGENERATION, level: 1, duration: 100, who: "@Self" } }]
            """;
        ReforgeDefReader.Parsed parsed = ReforgeDefReader.read("reforges/x", root(yaml, diags), counter(), diags);
        assertFalse(diags.hasErrors(), () -> diags.all().toString());
        // A support ability with no trigger joins the activation (USE), so it lands in useStableKeys + conditionSources.
        assertEquals(List.of("USE"), parsed.abilities().get(1).triggers());
        assertEquals(List.of("reforges/x", "reforges/x/a1"), parsed.def().useStableKeys());
        assertEquals(List.of("", ""), parsed.def().conditionSources());
    }

    @Test
    void missingDisplayIsError() {
        Diagnostics diags = new Diagnostics();
        ReforgeDefReader.Parsed parsed = ReforgeDefReader.read("reforges/x",
                root("effects: [{ POTION: { effect: SPEED, level: 1, duration: 100, who: \"@Self\" } }]\n", diags),
                counter(), diags);
        assertCode(diags, DiagCode.E_LOAD_REFORGE);
        assertNull(parsed.def());
        assertTrue(parsed.abilities().isEmpty());
    }

    @Test
    void nonMappingIsError() {
        Diagnostics diags = new Diagnostics();
        ReforgeDefReader.Parsed parsed = ReforgeDefReader.read("reforges/x", root("- a\n- b\n", diags), counter(), diags);
        assertCode(diags, DiagCode.E_LOAD_REFORGE);
        assertNull(parsed.def());
        assertTrue(parsed.abilities().isEmpty());
    }

    @Test
    void emptyAbilitiesListIsError() {
        Diagnostics diags = new Diagnostics();
        ReforgeDefReader.Parsed parsed =
                ReforgeDefReader.read("reforges/x", root("display: X\nabilities: []\n", diags), counter(), diags);
        assertCode(diags, DiagCode.E_LOAD_REFORGE);
        assertNull(parsed.def());
        assertTrue(parsed.abilities().isEmpty());
    }

    @Test
    void iconDefaultsAndMaterialAlias() {
        Diagnostics d1 = new Diagnostics();
        ReforgeDefReader.Parsed noIcon = ReforgeDefReader.read("reforges/x",
                root("display: X\neffects: [{ POTION: { effect: SPEED, level: 1, duration: 100, who: \"@Self\" } }]\n", d1),
                counter(), d1);
        assertEquals("ANVIL", noIcon.def().icon());          // icon defaults to ANVIL

        Diagnostics d2 = new Diagnostics();
        ReforgeDefReader.Parsed alias = ReforgeDefReader.read("reforges/x",
                root("display: X\nmaterial: SUGAR\neffects: [{ POTION: { effect: SPEED, level: 1, duration: 100, who: \"@Self\" } }]\n",
                        d2),
                counter(), d2);
        assertEquals("SUGAR", alias.def().icon());           // material: is an accepted alias for icon:
    }
}
