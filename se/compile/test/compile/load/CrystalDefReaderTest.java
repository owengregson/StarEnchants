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

/** Unit tests for the crystal reader (ADR-0014/0016): malformed input is a diagnostic, never an exception. */
class CrystalDefReaderTest {

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
    void validCrystalReadsToOneTierlessCrystalAbility() {
        Diagnostics diags = new Diagnostics();
        String yaml = """
            display: "&dZap"
            description:
              - "&d&lZAP CRYSTAL BONUS"
              - "&d* Shock on hit."
            tier: legendary
            applies-to: [SWORD]
            trigger: ATTACK
            group: shock
            chance: 30
            cooldown: 40
            effects: [{ DAMAGE: { amount: 4 } }]
            """;
        CrystalDefReader.Parsed parsed =
                CrystalDefReader.read("crystals/zap", root(yaml, diags), counter(), diags);

        assertFalse(diags.hasErrors(), () -> diags.all().toString());
        assertEquals("crystals/zap", parsed.def().key());
        assertEquals("&dZap", parsed.def().display());
        assertEquals("legendary", parsed.def().tier());
        assertEquals(List.of("SWORD"), parsed.def().appliesTo());
        assertEquals(List.of("&d&lZAP CRYSTAL BONUS", "&d* Shock on hit."), parsed.def().description());

        assertEquals(1, parsed.abilities().size());
        AbilityDef ability = parsed.abilities().get(0);
        assertEquals(SourceKind.CRYSTAL, ability.sourceKind());
        // a crystal has no levels, so its stable key is the bare base key — never a "/level" suffix (ADR-0016)
        assertEquals("crystals/zap", ability.stableKey());
        assertEquals(0, ability.level());
        assertEquals(30.0, ability.baseChance(), 1e-9);
        assertEquals(40, ability.cooldownTicks());
        assertEquals(List.of("ATTACK"), ability.triggers());
        assertEquals("shock", ability.cdScopeGroup());
        assertEquals("crystals/zap", ability.suppressKey());
        assertEquals(1, ability.effects().size());
        assertEquals("DAMAGE", ability.effects().get(0).head());
    }

    @Test
    void abilitiesListExpandsToBaseKeyThenSlashANLikeASet() {
        Diagnostics diags = new Diagnostics();
        String yaml = """
            display: "&cFlame"
            applies-to: [HELMET]
            abilities:
              - { trigger: ATTACK,  effects: [{ DAMAGE_MOD: { side: attack, mode: add, amount: 3 } }] }
              - { trigger: PASSIVE, effects: [{ POTION: { effect: FIRE_RESISTANCE, level: 1, duration: 200, who: "@Self" } }] }
            """;
        CrystalDefReader.Parsed parsed =
                CrystalDefReader.read("crystals/flame", root(yaml, diags), counter(), diags);

        assertFalse(diags.hasErrors(), () -> diags.all().toString());
        // A multi-ability crystal keys its first bonus to the base key, further ones to <base>/a1, /a2, … —
        // the dense chain the WornResolver walks, exactly like an armour set's extra armour bonuses (ADR-0034).
        assertEquals(2, parsed.abilities().size());
        assertEquals("crystals/flame", parsed.abilities().get(0).stableKey());
        assertEquals(List.of("ATTACK"), parsed.abilities().get(0).triggers());
        assertEquals("crystals/flame/a1", parsed.abilities().get(1).stableKey());
        assertEquals(List.of("PASSIVE"), parsed.abilities().get(1).triggers());
        assertEquals(SourceKind.CRYSTAL, parsed.abilities().get(1).sourceKind());
    }

    @Test
    void stackableDefaultsTrueAndParsesFalse() {
        // §ADR-0035: a crystal stacks unless it opts out — absent → true (every legacy crystal unchanged);
        // an explicit `stackable: false` blocks merge-with-self and per-wearer stacking.
        Diagnostics diags = new Diagnostics();
        String base = "display: \"&dX\"\napplies-to: [ARMOR]\ntrigger: ATTACK\neffects: [{ DAMAGE: { amount: 1 } }]\n";
        assertTrue(CrystalDefReader.read("crystals/def", root(base, diags), counter(), diags).def().stackable(),
                "absent stackable defaults to true");
        assertFalse(CrystalDefReader.read("crystals/nostack", root(base + "stackable: false\n", diags), counter(), diags)
                .def().stackable(), "stackable: false disables stacking");
    }

    @Test
    void displayDefaultsToTheBaseKeyWhenAbsent() {
        Diagnostics diags = new Diagnostics();
        String yaml = "trigger: ATTACK\neffects: [{ DAMAGE: { amount: 1 } }]\n";
        CrystalDefReader.Parsed parsed =
                CrystalDefReader.read("crystals/plain", root(yaml, diags), counter(), diags);

        assertFalse(diags.hasErrors(), () -> diags.all().toString());
        assertEquals("crystals/plain", parsed.def().display());
    }

    @Test
    void missingTriggerIsAnError() {
        Diagnostics diags = new Diagnostics();
        CrystalDefReader.read("crystals/x", root("effects: [{ DAMAGE: { amount: 1 } }]\n", diags), counter(), diags);
        assertCode(diags, DiagCode.E_LOAD_CRYSTAL_TRIGGER);
    }

    @Test
    void noEffectsWarnsButDoesNotBlock() {
        Diagnostics diags = new Diagnostics();
        CrystalDefReader.read("crystals/x", root("trigger: ATTACK\n", diags), counter(), diags);
        assertFalse(diags.hasErrors());
        assertCode(diags, DiagCode.W_LOAD_EFFECTS); // an effect-less crystal is warned, not silently accepted
    }

    @Test
    void nonMappingFileIsAnErrorAndYieldsNoAbility() {
        Diagnostics diags = new Diagnostics();
        CrystalDefReader.Parsed parsed =
                CrystalDefReader.read("crystals/x", root("- a\n- b\n", diags), counter(), diags);
        assertCode(diags, DiagCode.E_LOAD_CRYSTAL);
        assertNull(parsed.def());
        assertTrue(parsed.abilities().isEmpty());
    }
}
