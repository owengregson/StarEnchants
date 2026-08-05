package compile.load;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.IntSupplier;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import schema.diag.DiagCode;
import schema.diag.Diagnostics;

/**
 * R-QC59, one contract across all seven def readers: a MISSING {@code effects:} key still warns
 * {@code W_LOAD_EFFECTS} (the real omission), while an explicitly authored {@code effects: []} loads silently
 * (a deliberate shape — an ability whose whole job is a condition, a cooldown or a lore rung). A reader that
 * kept only one of the two arms is a structurally absent row here, not a missing file.
 */
class EmptyEffectsListTest {

    /** One reader under test: how to invoke it, and the two YAML bodies that differ only in the effects key. */
    private record Reader(String name, BiConsumer<String, Diagnostics> read, String absent, String empty) {
    }

    private static IntSupplier counter() {
        int[] id = {0};
        return () -> id[0]++;
    }

    private static YamlNode root(String yaml, Diagnostics diags) {
        return YamlNode.compose("test.yml", yaml, diags);
    }

    private static final List<Reader> READERS = List.of(
            new Reader("enchant",
                    (yaml, diags) -> EnchantDefReader.read("enchants/x", root(yaml, diags), counter(), diags),
                    "trigger: ATTACK\nlevels:\n  1:\n    cooldown: 20\n",
                    "trigger: ATTACK\nlevels:\n  1:\n    cooldown: 20\n    effects: []\n"),
            new Reader("mask",
                    (yaml, diags) -> MaskDefReader.read("masks/x", root(yaml, diags), counter(), diags),
                    "display: X\ntrigger: ATTACK\n",
                    "display: X\ntrigger: ATTACK\neffects: []\n"),
            new Reader("crystal",
                    (yaml, diags) -> CrystalDefReader.read("crystals/x", root(yaml, diags), counter(), diags),
                    "trigger: ATTACK\n",
                    "trigger: ATTACK\neffects: []\n"),
            new Reader("set",
                    (yaml, diags) -> SetDefReader.read("sets/x", root(yaml, diags), counter(), diags),
                    setYaml(""),
                    setYaml("    effects: []\n")),
            new Reader("pet",
                    (yaml, diags) -> PetDefReader.read("x", root(yaml, diags), counter(), diags),
                    petYaml("          - { trigger: PASSIVE }\n"),
                    petYaml("          - { trigger: PASSIVE, effects: [] }\n")),
            new Reader("reforge",
                    (yaml, diags) -> ReforgeDefReader.read("reforges/x", root(yaml, diags), counter(), diags),
                    reforgeYaml("  - trigger: PASSIVE\n"),
                    reforgeYaml("  - trigger: PASSIVE\n    effects: []\n")),
            new Reader("use-item",
                    (yaml, diags) -> UseItemDefReader.read("x", root(yaml, diags), counter(), diags),
                    "name: \"&aX\"\nmaterial: PAPER\n",
                    "name: \"&aX\"\nmaterial: PAPER\neffects: []\n"));

    private static String setYaml(String effectsLine) {
        return """
            display: "&bX"
            complete: 4
            armor:
              pieces:
                helmet: { material: DIAMOND_HELMET }
            bonuses:
              - on: armor
                trigger: DEFEND
            """ + effectsLine;
    }

    private static String petYaml(String abilityLine) {
        return """
            display: "X"
            type: ACTIVE
            levels:
              1:
                abilities:
            """ + abilityLine;
    }

    private static String reforgeYaml(String abilityBlock) {
        return """
            display: "X"
            icon: SUGAR
            abilities:
            """ + abilityBlock;
    }

    @TestFactory
    List<DynamicTest> aMissingKeyWarnsAndAnExplicitEmptyListDoesNot() {
        return READERS.stream().map(reader -> dynamicTest(reader.name(), () -> {
            Diagnostics absent = new Diagnostics();
            reader.read().accept(reader.absent(), absent);
            assertTrue(warned(absent), () -> "a missing effects: key must still warn — " + absent.all());

            Diagnostics empty = new Diagnostics();
            reader.read().accept(reader.empty(), empty);
            assertFalse(warned(empty), () -> "effects: [] is an authored shape, not an omission — " + empty.all());
            assertFalse(empty.hasErrors(), () -> empty.all().toString());
        })).toList();
    }

    private static boolean warned(Diagnostics diags) {
        return diags.all().stream().anyMatch(d -> d.is(DiagCode.W_LOAD_EFFECTS));
    }
}
