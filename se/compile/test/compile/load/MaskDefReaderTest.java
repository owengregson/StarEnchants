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

/** Unit tests for the mask reader (ADR-0014/0053): malformed input is a diagnostic, never an exception. */
class MaskDefReaderTest {

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
    void validMaskReadsToOneTierlessMaskAbility() {
        Diagnostics diags = new Diagnostics();
        String yaml = """
            display: "Midas"
            color: "&6"
            head: "aGVhZA=="
            description:
              - "&6&lMIDAS MASK"
              - "&6* Negates enemy heroic armor."
            trigger: ATTACK
            group: midas
            chance: 30
            cooldown: 40
            effects: [{ IGNORE_HEROIC: {} }]
            """;
        MaskDefReader.Parsed parsed = MaskDefReader.read("masks/midas", root(yaml, diags), counter(), diags);

        assertFalse(diags.hasErrors(), () -> diags.all().toString());
        assertEquals("masks/midas", parsed.def().key());
        assertEquals("Midas", parsed.def().display());
        assertEquals("&6", parsed.def().color());
        assertEquals("aGVhZA==", parsed.def().head());
        assertEquals(List.of("&6&lMIDAS MASK", "&6* Negates enemy heroic armor."), parsed.def().description());

        assertEquals(1, parsed.abilities().size());
        AbilityDef ability = parsed.abilities().get(0);
        assertEquals(SourceKind.MASK, ability.sourceKind());
        // a mask has no levels, so its stable key is the bare base key — never a "/level" suffix (ADR-0016)
        assertEquals("masks/midas", ability.stableKey());
        assertEquals(0, ability.level());
        assertEquals(30.0, ability.baseChance(), 1e-9);
        assertEquals(40, ability.cooldownTicks());
        assertEquals(List.of("ATTACK"), ability.triggers());
        assertEquals("midas", ability.cdScopeGroup());
        // each ability cooldown-scopes to its own stable key (no family-wide scope — masks have no USE path)
        assertEquals("masks/midas", ability.cdScopeEnchant());
        assertEquals("masks/midas", ability.suppressKey());
        assertEquals(1, ability.effects().size());
        assertEquals("IGNORE_HEROIC", ability.effects().get(0).head());
    }

    @Test
    void abilitiesListExpandsToBaseKeyThenSlashANLikeACrystal() {
        Diagnostics diags = new Diagnostics();
        String yaml = """
            display: "Chef"
            abilities:
              - { trigger: ATTACK,  effects: [{ SUPPRESS: { scope: KIND, key: MODIFY_FOOD } }] }
              - { trigger: DEFENSE, effects: [{ SUPPRESS: { scope: KIND, key: MODIFY_FOOD } }] }
            """;
        MaskDefReader.Parsed parsed = MaskDefReader.read("masks/chef", root(yaml, diags), counter(), diags);

        assertFalse(diags.hasErrors(), () -> diags.all().toString());
        // A multi-ability mask keys its first ability to the base key, further ones to <base>/a1, /a2, … — the
        // dense chain the WornResolver walks, exactly like a crystal's extra bonuses (ADR-0053).
        assertEquals(2, parsed.abilities().size());
        assertEquals("masks/chef", parsed.abilities().get(0).stableKey());
        assertEquals(List.of("ATTACK"), parsed.abilities().get(0).triggers());
        assertEquals("masks/chef/a1", parsed.abilities().get(1).stableKey());
        assertEquals(List.of("DEFENSE"), parsed.abilities().get(1).triggers());
        assertEquals(SourceKind.MASK, parsed.abilities().get(1).sourceKind());
    }

    @Test
    void shorthandFormReadsOneTopLevelAbility() {
        // the crystal dual form: a single-ability mask authors trigger + effects at the top level
        Diagnostics diags = new Diagnostics();
        String yaml = """
            display: "Blaze"
            color: "&c"
            trigger: FIRE
            effects: [{ CANCEL: {} }]
            """;
        MaskDefReader.Parsed parsed = MaskDefReader.read("masks/blaze", root(yaml, diags), counter(), diags);

        assertFalse(diags.hasErrors(), () -> diags.all().toString());
        assertEquals(1, parsed.abilities().size());
        assertEquals("masks/blaze", parsed.abilities().get(0).stableKey());
        assertEquals(List.of("FIRE"), parsed.abilities().get(0).triggers());
    }

    @Test
    void defaultsFillColorAndMaterialWhenAbsent() {
        Diagnostics diags = new Diagnostics();
        String yaml = "display: Plain\ntrigger: PASSIVE\neffects: [{ POTION: { effect: SPEED, level: 1, duration: 200, who: \"@Self\" } }]\n";
        MaskDefReader.Parsed parsed = MaskDefReader.read("masks/plain", root(yaml, diags), counter(), diags);

        assertFalse(diags.hasErrors(), () -> diags.all().toString());
        assertEquals("&f", parsed.def().color());         // colour defaults white
        assertEquals("PLAYER_HEAD", parsed.def().material()); // material defaults to the player-head token
        assertEquals("", parsed.def().head());            // head defaults empty (material fallback)
        assertTrue(parsed.def().description().isEmpty()); // description defaults to the empty list
    }

    @Test
    void missingDisplayIsAnErrorAndYieldsNoDef() {
        // unlike a crystal (which falls back to the base key), a mask REQUIRES a display
        Diagnostics diags = new Diagnostics();
        MaskDefReader.Parsed parsed =
                MaskDefReader.read("masks/x", root("trigger: ATTACK\neffects: [{ CANCEL: {} }]\n", diags), counter(), diags);
        assertCode(diags, DiagCode.E_LOAD_MASK);
        assertNull(parsed.def());
        assertTrue(parsed.abilities().isEmpty());
    }

    @Test
    void missingTriggerIsAnError() {
        Diagnostics diags = new Diagnostics();
        MaskDefReader.read("masks/x", root("display: X\neffects: [{ CANCEL: {} }]\n", diags), counter(), diags);
        assertCode(diags, DiagCode.E_LOAD_MASK);
    }

    @Test
    void emptyAbilitiesListIsAnErrorAndYieldsNoDef() {
        Diagnostics diags = new Diagnostics();
        MaskDefReader.Parsed parsed =
                MaskDefReader.read("masks/x", root("display: X\nabilities: []\n", diags), counter(), diags);
        assertCode(diags, DiagCode.E_LOAD_MASK);
        assertNull(parsed.def());
        assertTrue(parsed.abilities().isEmpty());
    }

    @Test
    void noEffectsWarnsButDoesNotBlock() {
        Diagnostics diags = new Diagnostics();
        MaskDefReader.read("masks/x", root("display: X\ntrigger: ATTACK\n", diags), counter(), diags);
        assertFalse(diags.hasErrors());
        assertCode(diags, DiagCode.W_LOAD_EFFECTS); // an effect-less mask is warned, not silently accepted
    }

    @Test
    void nonMappingFileIsAnErrorAndYieldsNoAbility() {
        Diagnostics diags = new Diagnostics();
        MaskDefReader.Parsed parsed = MaskDefReader.read("masks/x", root("- a\n- b\n", diags), counter(), diags);
        assertCode(diags, DiagCode.E_LOAD_MASK);
        assertNull(parsed.def());
        assertTrue(parsed.abilities().isEmpty());
    }
}
