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

    @Test
    void validCrystalReadsToOneTierlessCrystalAbility() {
        Diagnostics diags = new Diagnostics();
        String yaml = """
            display: "&dZap"
            description: "Shock on hit."
            tier: legendary
            material: DIAMOND
            name: "&dZap Crystal"
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
        assertTrue(diags.hasErrors());
    }

    @Test
    void noEffectsWarnsButDoesNotBlock() {
        Diagnostics diags = new Diagnostics();
        CrystalDefReader.read("crystals/x", root("trigger: ATTACK\n", diags), counter(), diags);
        assertFalse(diags.hasErrors());
        assertFalse(diags.all().isEmpty(), "an effect-less crystal is warned, not silently accepted");
    }

    @Test
    void nonMappingFileIsAnErrorAndYieldsNoAbility() {
        Diagnostics diags = new Diagnostics();
        CrystalDefReader.Parsed parsed =
                CrystalDefReader.read("crystals/x", root("- a\n- b\n", diags), counter(), diags);
        assertTrue(diags.hasErrors());
        assertNull(parsed.def());
        assertTrue(parsed.abilities().isEmpty());
    }
}
