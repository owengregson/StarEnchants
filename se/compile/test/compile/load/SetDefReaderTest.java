package compile.load;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.def.AbilityDef;
import compile.model.SourceKind;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;
import org.junit.jupiter.api.Test;
import schema.diag.DiagCode;
import schema.diag.Diagnostics;

/** Unit tests for the armour-set reader (ADR-0014): malformed input is a diagnostic, never an exception. */
class SetDefReaderTest {

    private static YamlNode root(String yaml, Diagnostics diags) {
        return YamlNode.compose("test.yml", yaml, diags);
    }

    private static IntSupplier counter() {
        int[] id = {0};
        return () -> id[0]++;
    }

    @Test
    void validSetWithArmorAndWeaponReadsToTwoBonuses() {
        Diagnostics diags = new Diagnostics();
        String yaml = """
            display: "&bYeti"
            description: "Frost set."
            complete: 4
            armor:
              trigger: DEFEND
              lore: ["&7Frost aura"]
              pieces:
                helmet: { material: DIAMOND_HELMET, name: "&bYeti Helm" }
                chestplate: { material: DIAMOND_CHESTPLATE }
                leggings: { material: DIAMOND_LEGGINGS }
                boots: { material: DIAMOND_BOOTS }
              effects: [{ DAMAGE: { amount: 2 } }]
            weapon:
              material: DIAMOND_SWORD
              name: "&bYeti Blade"
              trigger: ATTACK
              effects: [{ HEAL: { amount: 1 } }]
            """;
        SetDefReader.Parsed parsed = SetDefReader.read("sets/yeti", root(yaml, diags), counter(), diags);

        assertFalse(diags.hasErrors(), () -> diags.all().toString());
        assertEquals("sets/yeti", parsed.def().key());
        assertEquals("&bYeti", parsed.def().display());
        assertEquals(4, parsed.def().armorComplete());
        assertEquals(4, parsed.def().armorMembers().size());
        // slot tokens are uppercased and ordered as authored
        assertEquals(List.of("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"), parsed.def().appliesTo());
        assertTrue(parsed.def().hasWeapon());
        assertEquals("DIAMOND_SWORD", parsed.def().weapon().material());

        // armour bonus -> <key> with the worn-piece count on setPieces; weapon bonus -> <key>/weapon, setPieces 0
        assertEquals(2, parsed.abilities().size());
        AbilityDef armor = parsed.abilities().get(0);
        assertEquals(SourceKind.SET, armor.sourceKind());
        assertEquals("sets/yeti", armor.stableKey());
        assertEquals(4, armor.setPieces());
        assertEquals(List.of("DEFEND"), armor.triggers());

        AbilityDef weapon = parsed.abilities().get(1);
        assertEquals("sets/yeti/weapon", weapon.stableKey());
        assertEquals(0, weapon.setPieces());
        assertEquals(List.of("ATTACK"), weapon.triggers());
    }

    @Test
    void armorOnlySetReadsToOneBonus() {
        Diagnostics diags = new Diagnostics();
        String yaml = """
            armor:
              trigger: DEFEND
              pieces:
                boots: { material: LEATHER_BOOTS }
              effects: [{ DAMAGE: { amount: 1 } }]
            """;
        SetDefReader.Parsed parsed = SetDefReader.read("sets/light", root(yaml, diags), counter(), diags);

        assertFalse(diags.hasErrors(), () -> diags.all().toString());
        assertFalse(parsed.def().hasWeapon());
        assertEquals(1, parsed.abilities().size());
        // an omitted `complete` defaults to the worn-piece count
        assertEquals(1, parsed.def().armorComplete());
    }

    @Test
    void missingArmorBlockIsAnError() {
        Diagnostics diags = new Diagnostics();
        SetDefReader.read("sets/x", root("display: Nope\n", diags), counter(), diags);
        assertTrue(diags.hasErrors());
    }

    @Test
    void armorPieceWithoutMaterialIsAnError() {
        Diagnostics diags = new Diagnostics();
        String yaml = """
            armor:
              trigger: DEFEND
              pieces:
                helmet: { name: "&bNo material" }
              effects: [{ DAMAGE: { amount: 1 } }]
            """;
        SetDefReader.read("sets/x", root(yaml, diags), counter(), diags);
        assertTrue(diags.hasErrors());
    }

    @Test
    void nonPositiveCompleteIsAnError() {
        Diagnostics diags = new Diagnostics();
        String yaml = """
            complete: 0
            armor:
              trigger: DEFEND
              pieces:
                boots: { material: LEATHER_BOOTS }
              effects: [{ DAMAGE: { amount: 1 } }]
            """;
        SetDefReader.read("sets/x", root(yaml, diags), counter(), diags);
        assertTrue(diags.hasErrors());
    }

    @Test
    void weaponWithoutMaterialIsAnError() {
        Diagnostics diags = new Diagnostics();
        String yaml = """
            armor:
              trigger: DEFEND
              pieces:
                boots: { material: LEATHER_BOOTS }
              effects: [{ DAMAGE: { amount: 1 } }]
            weapon:
              trigger: ATTACK
              effects: [{ HEAL: { amount: 1 } }]
            """;
        SetDefReader.read("sets/x", root(yaml, diags), counter(), diags);
        assertTrue(diags.hasErrors());
    }

    @Test
    void enchantsBlockParsesToTheRefLevelMapPreservingAuthoredOrder() {
        Diagnostics diags = new Diagnostics();
        String yaml = """
            armor:
              enchants:
                enchants/frost: 2
                PROTECTION: 4
              trigger: DEFEND
              pieces:
                boots: { material: DIAMOND_BOOTS }
              effects: [{ DAMAGE: { amount: 1 } }]
            weapon:
              enchants:
                SHARPNESS: 5
              material: DIAMOND_SWORD
              trigger: ATTACK
              effects: [{ HEAL: { amount: 1 } }]
            """;
        SetDefReader.Parsed parsed = SetDefReader.read("sets/frost", root(yaml, diags), counter(), diags);

        assertFalse(diags.hasErrors(), () -> diags.all().toString());
        // ref→level, authored order preserved — it determines the minted piece's enchant lore order
        assertEquals(List.of("enchants/frost", "PROTECTION"), List.copyOf(parsed.def().armorEnchants().keySet()));
        assertEquals(2, parsed.def().armorEnchants().get("enchants/frost"));
        assertEquals(Map.of("SHARPNESS", 5), parsed.def().weaponEnchants());
    }

    @Test
    void aNonNumericEnchantLevelWarnsByCodeAndIsSkipped() {
        Diagnostics diags = new Diagnostics();
        String yaml = """
            armor:
              enchants:
                enchants/frost: nope
                PROTECTION: 4
              trigger: DEFEND
              pieces:
                boots: { material: DIAMOND_BOOTS }
              effects: [{ DAMAGE: { amount: 1 } }]
            """;
        SetDefReader.Parsed parsed = SetDefReader.read("sets/frost", root(yaml, diags), counter(), diags);

        assertFalse(diags.hasErrors(), "a non-numeric level is a warning, not a blocking error");
        assertTrue(diags.all().stream().anyMatch(d -> d.is(DiagCode.W_SET_ENCHANT)), () -> diags.all().toString());
        // the unparseable entry is dropped; its valid sibling survives
        assertEquals(Map.of("PROTECTION", 4), parsed.def().armorEnchants());
    }

    @Test
    void nonMappingFileIsAnErrorAndYieldsNoBonus() {
        Diagnostics diags = new Diagnostics();
        SetDefReader.Parsed parsed = SetDefReader.read("sets/x", root("- a\n- b\n", diags), counter(), diags);
        assertTrue(diags.hasErrors());
        assertNull(parsed.def());
        assertTrue(parsed.abilities().isEmpty());
    }
}
