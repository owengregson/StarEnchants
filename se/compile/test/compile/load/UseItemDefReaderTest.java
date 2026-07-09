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
import schema.grammar.EffectLine;

/** Unit tests for the use-item reader (§3.4): malformed input is a diagnostic, never an exception. */
class UseItemDefReaderTest {

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
    void shorthandReadsLikenessAndOneUseAbilityForcedToUse() {
        Diagnostics diags = new Diagnostics();
        String yaml = """
            name: "&c&lRage Crystal"
            material: RED_DYE
            consumable: true
            shiny: false
            lore:
              - "&eConsume to gain strength."
              - "&eCooldown: {TIME_FORMATTED}"
            permission: "se.rage"
            cooldown: 1200
            chance: 80
            condition: "%actor.health% > 4"
            effects: [{ POTION: { effect: INCREASE_DAMAGE, level: 2, duration: 240, who: "@Self" } }]
            """;
        UseItemDefReader.Parsed parsed = UseItemDefReader.read("rage-crystal", root(yaml, diags), counter(), diags);

        assertFalse(diags.hasErrors(), () -> diags.all().toString());
        UseItemDef def = parsed.def();
        assertEquals("rage-crystal", def.key());
        assertEquals("&c&lRage Crystal", def.name());
        assertEquals("RED_DYE", def.material());
        assertEquals(List.of("&eConsume to gain strength.", "&eCooldown: {TIME_FORMATTED}"), def.lore());
        assertTrue(def.consumable());
        assertFalse(def.shiny());
        assertEquals("se.rage", def.permission());
        assertEquals(1200, def.cooldownTicks());
        assertEquals(List.of("use:rage-crystal"), def.stableKeys());
        assertEquals(List.of("%actor.health% > 4"), def.conditionSources());

        assertEquals(1, parsed.abilities().size());
        AbilityDef a0 = parsed.abilities().get(0);
        assertEquals(SourceKind.USE_ITEM, a0.sourceKind());
        assertEquals("use:rage-crystal", a0.stableKey());
        assertEquals(0, a0.level());
        assertEquals(80.0, a0.baseChance(), 1e-9);
        assertEquals(1200, a0.cooldownTicks());
        // USE is implicit — the ability always fires on right-click, never any authored trigger.
        assertEquals(List.of("USE"), a0.triggers());
        assertEquals("use:rage-crystal", a0.suppressKey());
        assertEquals(1, a0.effects().size());
        assertEquals("POTION", a0.effects().get(0).head());
    }

    @Test
    void omittedFlagsTakeTheDocumentedDefaults() {
        // §3.7: consumable true, shiny false, is-food false, permission "" when omitted.
        Diagnostics diags = new Diagnostics();
        String yaml = """
            name: "&aTool"
            material: STICK
            effects: [{ HEAL: { amount: 1, who: "@Self" } }]
            """;
        UseItemDef def = UseItemDefReader.read("tool", root(yaml, diags), counter(), diags).def();

        assertFalse(diags.hasErrors(), () -> diags.all().toString());
        assertTrue(def.consumable(), "consumable defaults true");
        assertFalse(def.shiny(), "shiny defaults false");
        assertFalse(def.isFood(), "is-food defaults false");
        assertEquals("", def.permission());
        assertEquals(0, def.cooldownTicks());
    }

    @Test
    void isFoodParsesTrueAndFalse() {
        Diagnostics on = new Diagnostics();
        UseItemDef eaten = UseItemDefReader.read("eaten",
                root("name: \"&aE\"\nmaterial: APPLE\nis-food: true\neffects: []\n", on), counter(), on).def();
        assertFalse(on.hasErrors(), () -> on.all().toString());
        assertTrue(eaten.isFood());

        Diagnostics off = new Diagnostics();
        UseItemDef clicked = UseItemDefReader.read("clicked",
                root("name: \"&aC\"\nmaterial: STICK\nis-food: false\neffects: []\n", off), counter(), off).def();
        assertFalse(off.hasErrors(), () -> off.all().toString());
        assertFalse(clicked.isFood());
    }

    @Test
    void aBadIsFoodValueWarnsAndFallsBackToFalse() {
        Diagnostics diags = new Diagnostics();
        UseItemDef def = UseItemDefReader.read("x",
                root("name: \"&aX\"\nmaterial: STICK\nis-food: maybe\neffects: []\n", diags), counter(), diags).def();
        assertCode(diags, DiagCode.W_LOAD_BOOL);
        assertFalse(diags.hasErrors(), "a bad boolean is a warning, not a block");
        assertFalse(def.isFood(), "a bad is-food value falls back to the false default");
    }

    @Test
    void anUnknownRootKeyIsStillFlaggedAlongsideIsFood() {
        // is-food joined ROOT_KEYS, but a genuinely-unknown key must still warn (ROOT_KEYS did not go permissive).
        Diagnostics diags = new Diagnostics();
        UseItemDefReader.read("x",
                root("name: \"&aX\"\nmaterial: STICK\nis-food: true\nnonsense: 1\neffects: []\n", diags),
                counter(), diags);
        assertCode(diags, DiagCode.W_UNKNOWN_KEY);
    }

    @Test
    void abilitiesListExpandsToUseKeyThenSlashAn() {
        Diagnostics diags = new Diagnostics();
        String yaml = """
            name: "&bMulti"
            material: PAPER
            abilities:
              - { condition: "%actor.sneaking%", effects: [{ HEAL: { amount: 2, who: "@Self" } }] }
              - { effects: [{ POTION: { effect: SPEED, level: 1, duration: 100, who: "@Self" } }] }
            """;
        UseItemDefReader.Parsed parsed = UseItemDefReader.read("multi", root(yaml, diags), counter(), diags);

        assertFalse(diags.hasErrors(), () -> diags.all().toString());
        assertEquals(2, parsed.abilities().size());
        assertEquals("use:multi", parsed.abilities().get(0).stableKey());
        assertEquals("use:multi/a1", parsed.abilities().get(1).stableKey());
        assertEquals(List.of("USE"), parsed.abilities().get(1).triggers());
        assertEquals(List.of("use:multi", "use:multi/a1"), parsed.def().stableKeys());
        // conditionSources align to stableKeys: an ability with no condition contributes "".
        assertEquals(List.of("%actor.sneaking%", ""), parsed.def().conditionSources());
    }

    @Test
    void stringCommandLowersToConsoleRunCommandAppendedAfterAuthoredEffects() {
        Diagnostics diags = new Diagnostics();
        String yaml = """
            name: "&aGiver"
            material: PAPER
            effects: [{ HEAL: { amount: 1, who: "@Self" } }]
            commands:
              - "say hello {PLAYER}"
            """;
        AbilityDef a0 = UseItemDefReader.read("giver", root(yaml, diags), counter(), diags).abilities().get(0);

        assertFalse(diags.hasErrors(), () -> diags.all().toString());
        assertEquals(2, a0.effects().size());
        assertEquals("HEAL", a0.effects().get(0).head(), "authored effect stays first");
        EffectLine cmd = a0.effects().get(1);
        assertEquals("RUN_COMMAND", cmd.head());
        assertEquals("say hello {PLAYER}", cmd.named().get("command"));
        assertEquals("console", cmd.named().get("as"));
    }

    @Test
    void commandMapHonoursItsAsField() {
        Diagnostics diags = new Diagnostics();
        String yaml = """
            name: "&aGiver"
            material: PAPER
            effects: []
            commands:
              - { as: player, run: "spawn" }
            """;
        AbilityDef a0 = UseItemDefReader.read("giver", root(yaml, diags), counter(), diags).abilities().get(0);

        EffectLine cmd = a0.effects().get(0);
        assertEquals("RUN_COMMAND", cmd.head());
        assertEquals("spawn", cmd.named().get("command"));
        assertEquals("player", cmd.named().get("as"));
    }

    @Test
    void authoredTriggerIsWarnedAndForcedToUse() {
        Diagnostics diags = new Diagnostics();
        String yaml = """
            name: "&aX"
            material: PAPER
            trigger: ATTACK
            effects: [{ HEAL: { amount: 1, who: "@Self" } }]
            """;
        AbilityDef a0 = UseItemDefReader.read("x", root(yaml, diags), counter(), diags).abilities().get(0);

        assertCode(diags, DiagCode.W_LOAD_USE_TRIGGER);
        assertFalse(diags.hasErrors(), "forcing the trigger is a warning, not a block");
        assertEquals(List.of("USE"), a0.triggers());
    }

    @Test
    void topLevelTriggerWithAnAbilitiesListIsAlsoWarnedAndIgnored() {
        // The per-block trigger check never sees a root-level trigger in the abilities-list form; warn there too.
        Diagnostics diags = new Diagnostics();
        String yaml = """
            name: "&aX"
            material: PAPER
            trigger: ATTACK
            abilities:
              - { effects: [{ HEAL: { amount: 1, who: "@Self" } }] }
            """;
        AbilityDef a0 = UseItemDefReader.read("x", root(yaml, diags), counter(), diags).abilities().get(0);

        assertCode(diags, DiagCode.W_LOAD_USE_TRIGGER);
        assertFalse(diags.hasErrors(), "forcing the trigger is a warning, not a block");
        assertEquals(List.of("USE"), a0.triggers());
    }

    @Test
    void aNonIntegerCooldownIsABlockingDiagnostic() {
        Diagnostics diags = new Diagnostics();
        String yaml = """
            name: "&aX"
            material: PAPER
            cooldown: soon
            effects: [{ HEAL: { amount: 1, who: "@Self" } }]
            """;
        UseItemDefReader.read("x", root(yaml, diags), counter(), diags);
        assertCode(diags, DiagCode.E_LOAD_INT);
    }

    @Test
    void noEffectsWarnsButDoesNotBlock() {
        Diagnostics diags = new Diagnostics();
        String yaml = "name: \"&aX\"\nmaterial: PAPER\n";
        UseItemDefReader.read("x", root(yaml, diags), counter(), diags);
        assertFalse(diags.hasErrors());
        assertCode(diags, DiagCode.W_LOAD_EFFECTS);
    }

    @Test
    void missingNameOrMaterialSkipsTheWholeDef() {
        Diagnostics diags = new Diagnostics();
        UseItemDefReader.Parsed noMaterial =
                UseItemDefReader.read("x", root("name: \"&aX\"\n", diags), counter(), diags);
        assertCode(diags, DiagCode.E_LOAD_USE_ITEM);
        assertNull(noMaterial.def());
        assertTrue(noMaterial.abilities().isEmpty());

        Diagnostics diags2 = new Diagnostics();
        UseItemDefReader.Parsed noName =
                UseItemDefReader.read("x", root("material: PAPER\n", diags2), counter(), diags2);
        assertCode(diags2, DiagCode.E_LOAD_USE_ITEM);
        assertNull(noName.def());
    }

    @Test
    void nonMappingFileIsAnErrorAndYieldsNoDef() {
        Diagnostics diags = new Diagnostics();
        UseItemDefReader.Parsed parsed = UseItemDefReader.read("x", root("- a\n- b\n", diags), counter(), diags);
        assertCode(diags, DiagCode.E_LOAD_USE_ITEM);
        assertNull(parsed.def());
        assertTrue(parsed.abilities().isEmpty());
    }
}
