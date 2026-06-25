package compile.load;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.def.AbilityDef;
import java.util.List;
import java.util.function.IntSupplier;
import org.junit.jupiter.api.Test;
import schema.diag.Diagnostics;

/** Unit tests for the enchant reader (ADR-0014): malformed input is a diagnostic, never an exception. */
class EnchantDefReaderTest {

    private static YamlNode root(String yaml, Diagnostics diags) {
        return YamlNode.compose("test.yml", yaml, diags);
    }

    private static IntSupplier counter() {
        int[] id = {0};
        return () -> id[0]++;
    }

    @Test
    void validEnchantExpandsToOneAbilityPerLevel() {
        Diagnostics diags = new Diagnostics();
        String yaml = """
            display: "&cLifesteal"
            description: "Heal on hit."
            trigger: ATTACK
            applies-to: [SWORD, AXE]
            group: combat
            levels:
              1: { chance: 10, cooldown: 40, effects: ["HEAL:@Self:2"] }
              2: { chance: 15, effects: ["HEAL:@Self:4", "MESSAGE:@Self:hi"] }
              3: { chance: 20, effects: ["HEAL:@Self:6"] }
            """;
        EnchantDefReader.Parsed parsed =
                EnchantDefReader.read("enchants/lifesteal", root(yaml, diags), counter(), diags);

        assertFalse(diags.hasErrors(), () -> diags.all().toString());
        assertEquals("enchants/lifesteal", parsed.def().key());
        assertEquals("&cLifesteal", parsed.def().display());
        assertEquals(List.of("SWORD", "AXE"), parsed.def().appliesTo());
        assertEquals(3, parsed.def().maxLevel());

        List<AbilityDef> abilities = parsed.abilities();
        assertEquals(3, abilities.size());
        AbilityDef level2 = abilities.get(1);
        assertEquals("enchants/lifesteal/2", level2.stableKey());
        assertEquals(2, level2.level());
        assertEquals(15.0, level2.baseChance(), 1e-9);
        assertEquals(List.of("ATTACK"), level2.triggers());
        assertEquals("enchants/lifesteal", level2.suppressKey());
        assertEquals("enchants/lifesteal", level2.cdScopeEnchant());
        assertEquals("combat", level2.cdScopeGroup());
        assertEquals(2, level2.effects().size());
        assertEquals("HEAL", level2.effects().get(0).head());
        assertEquals(40, abilities.get(0).cooldownTicks());
    }

    @Test
    void missingTriggerIsAnError() {
        Diagnostics diags = new Diagnostics();
        String yaml = "levels:\n  1: { chance: 10, effects: [\"HEAL:2\"] }\n";
        EnchantDefReader.read("enchants/x", root(yaml, diags), counter(), diags);
        assertTrue(diags.hasErrors());
    }

    @Test
    void missingLevelsIsAnError() {
        Diagnostics diags = new Diagnostics();
        EnchantDefReader.read("enchants/x", root("trigger: ATTACK\n", diags), counter(), diags);
        assertTrue(diags.hasErrors());
    }

    @Test
    void outOfRangeChanceIsReported() {
        Diagnostics diags = new Diagnostics();
        String yaml = """
            trigger: ATTACK
            levels:
              1: { chance: 150, effects: ["HEAL:2"] }
            """;
        EnchantDefReader.read("enchants/x", root(yaml, diags), counter(), diags);
        assertTrue(diags.hasErrors());
    }

    @Test
    void duplicateKeysParseAsLastWins() {
        Diagnostics diags = new Diagnostics();
        // SnakeYAML 2.x rejects dup keys by default, 1.x does not; loader forces
        // allow-duplicate-keys so a file behaves identically on every server's SnakeYAML
        String yaml = """
            trigger: ATTACK
            levels:
              1: { chance: 10, chance: 25, effects: ["HEAL:2"] }
            """;
        EnchantDefReader.Parsed parsed =
                EnchantDefReader.read("enchants/x", root(yaml, diags), counter(), diags);
        assertFalse(diags.hasErrors(), () -> diags.all().toString());
        assertEquals(25.0, parsed.abilities().get(0).baseChance(), 1e-9);
    }

    @Test
    void nanChanceIsReported() {
        Diagnostics diags = new Diagnostics();
        String yaml = """
            trigger: ATTACK
            levels:
              1: { chance: NaN, effects: ["HEAL:2"] }
            """;
        EnchantDefReader.read("enchants/x", root(yaml, diags), counter(), diags);
        assertTrue(diags.hasErrors());
    }

    @Test
    void nonMappingFileIsAnError() {
        Diagnostics diags = new Diagnostics();
        EnchantDefReader.Parsed parsed =
                EnchantDefReader.read("enchants/x", root("- just\n- a\n- list\n", diags), counter(), diags);
        assertTrue(diags.hasErrors());
        assertTrue(parsed.abilities().isEmpty());
    }
}
