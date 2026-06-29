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

    /** Assert a diagnostic with the given code was emitted — the contract, not just "something failed". */
    private static void assertCode(Diagnostics diags, DiagCode code) {
        assertTrue(diags.all().stream().anyMatch(d -> d.is(code)), () -> diags.all().toString());
    }

    @Test
    void validSetWithArmorAndWeaponReadsToTwoBonuses() {
        Diagnostics diags = new Diagnostics();
        String yaml = """
            display: "&bYeti"
            description: "Frost set."
            complete: 4
            armor:
              lore: ["&7Frost aura"]
              pieces:
                helmet: { material: DIAMOND_HELMET, name: "&bYeti Helm" }
                chestplate: { material: DIAMOND_CHESTPLATE }
                leggings: { material: DIAMOND_LEGGINGS }
                boots: { material: DIAMOND_BOOTS }
            weapon:
              material: DIAMOND_SWORD
              name: "&bYeti Blade"
            bonuses:
              - on: armor
                trigger: DEFEND
                effects: [{ DAMAGE: { amount: 2 } }]
              - on: weapon
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

        // first on:armor bonus -> <key> with the worn-piece count on setPieces; on:weapon bonus -> <key>/w1, setPieces 0
        assertEquals(2, parsed.abilities().size());
        AbilityDef armor = parsed.abilities().get(0);
        assertEquals(SourceKind.SET, armor.sourceKind());
        assertEquals("sets/yeti", armor.stableKey());
        assertEquals(4, armor.setPieces());
        assertEquals(List.of("DEFEND"), armor.triggers());

        AbilityDef weapon = parsed.abilities().get(1);
        assertEquals("sets/yeti/w1", weapon.stableKey());
        assertEquals(0, weapon.setPieces());
        assertEquals(List.of("ATTACK"), weapon.triggers());
    }

    @Test
    void multipleBonusesUnderOneSetEachGetTheirOwnAbilityGatedByCompletion() {
        // The new capability: a permanent on:armor passive PLUS a cooldown-gated on:armor bonus PLUS an
        // on:weapon bonus, all under one completion gate. The primary keeps the set key; the rest get /aN, /wN.
        Diagnostics diags = new Diagnostics();
        String yaml = """
            complete: 4
            armor:
              pieces:
                boots: { material: DIAMOND_BOOTS }
            weapon:
              material: DIAMOND_SWORD
            bonuses:
              - on: armor
                trigger: DEFENSE
                chance: 100
                effects: [{ DAMAGE_MOD: { side: defense, mode: add, amount: 20 } }]
              - on: armor
                trigger: ATTACK
                chance: 25
                cooldown: 100
                effects: [{ DAMAGE: { amount: 5 } }]
              - on: weapon
                trigger: ATTACK
                effects: [{ HEAL: { amount: 1 } }]
            """;
        SetDefReader.Parsed parsed = SetDefReader.read("sets/devil", root(yaml, diags), counter(), diags);

        assertFalse(diags.hasErrors(), () -> diags.all().toString());
        assertEquals(3, parsed.abilities().size());

        AbilityDef primary = parsed.abilities().get(0);
        assertEquals("sets/devil", primary.stableKey()); // completion ability
        assertEquals(4, primary.setPieces());
        assertEquals(0, primary.cooldownTicks());

        AbilityDef extraArmor = parsed.abilities().get(1);
        assertEquals("sets/devil/a1", extraArmor.stableKey()); // gated on completion, not its own piece count
        assertEquals(0, extraArmor.setPieces());
        assertEquals(100, extraArmor.cooldownTicks());

        AbilityDef weapon = parsed.abilities().get(2);
        assertEquals("sets/devil/w1", weapon.stableKey());
        assertEquals(0, weapon.setPieces());
    }

    @Test
    void armorOnlySetReadsToOneBonus() {
        Diagnostics diags = new Diagnostics();
        String yaml = """
            armor:
              pieces:
                boots: { material: LEATHER_BOOTS }
            bonuses:
              - on: armor
                trigger: DEFEND
                effects: [{ DAMAGE: { amount: 1 } }]
            """;
        SetDefReader.Parsed parsed = SetDefReader.read("sets/light", root(yaml, diags), counter(), diags);

        assertFalse(diags.hasErrors(), () -> diags.all().toString());
        assertFalse(parsed.def().hasWeapon());
        assertEquals(1, parsed.abilities().size());
        // an omitted `complete` defaults to the worn-piece count
        assertEquals(1, parsed.def().armorComplete());
        // announce is opt-in; an omitted toggle defaults off with empty messages
        assertFalse(parsed.def().announce());
        assertEquals("", parsed.def().equipMessage());
    }

    @Test
    void parsesTheEquipRemoveAnnouncement() {
        Diagnostics diags = new Diagnostics();
        String yaml = """
            announce: true
            equip-message: "\\n&4 Devil SET EQUIPPED\\n"
            remove-message: "\\n&7 Devil SET REMOVED\\n"
            armor:
              pieces:
                boots: { material: DIAMOND_BOOTS }
            bonuses:
              - on: armor
                trigger: DEFENSE
                effects: [{ DAMAGE: { amount: 1 } }]
            """;
        SetDefReader.Parsed parsed = SetDefReader.read("sets/devil", root(yaml, diags), counter(), diags);

        assertFalse(diags.hasErrors(), () -> diags.all().toString());
        assertTrue(parsed.def().announce());
        // double-quoted \n is loaded as a real newline (the authored padding)
        assertEquals("\n&4 Devil SET EQUIPPED\n", parsed.def().equipMessage());
        assertEquals("\n&7 Devil SET REMOVED\n", parsed.def().removeMessage());
    }

    @Test
    void missingArmorBonusIsAnError() {
        Diagnostics diags = new Diagnostics();
        SetDefReader.read("sets/x", root("display: Nope\n", diags), counter(), diags);
        assertCode(diags, DiagCode.E_LOAD_SET_ARMOR);
    }

    @Test
    void armorPieceWithoutMaterialIsAnError() {
        Diagnostics diags = new Diagnostics();
        String yaml = """
            armor:
              pieces:
                helmet: { name: "&bNo material" }
            bonuses:
              - on: armor
                trigger: DEFEND
                effects: [{ DAMAGE: { amount: 1 } }]
            """;
        SetDefReader.read("sets/x", root(yaml, diags), counter(), diags);
        assertCode(diags, DiagCode.E_LOAD_SET_MEMBER);
    }

    @Test
    void nonPositiveCompleteIsAnError() {
        Diagnostics diags = new Diagnostics();
        String yaml = """
            complete: 0
            armor:
              pieces:
                boots: { material: LEATHER_BOOTS }
            bonuses:
              - on: armor
                trigger: DEFEND
                effects: [{ DAMAGE: { amount: 1 } }]
            """;
        SetDefReader.read("sets/x", root(yaml, diags), counter(), diags);
        assertCode(diags, DiagCode.E_LOAD_SET_COMPLETE);
    }

    @Test
    void weaponItemWithoutMaterialIsAnError() {
        Diagnostics diags = new Diagnostics();
        String yaml = """
            armor:
              pieces:
                boots: { material: LEATHER_BOOTS }
            weapon:
              name: "&cNo material"
            bonuses:
              - on: armor
                trigger: DEFEND
                effects: [{ DAMAGE: { amount: 1 } }]
            """;
        SetDefReader.read("sets/x", root(yaml, diags), counter(), diags);
        assertCode(diags, DiagCode.E_LOAD_SET_WEAPON);
    }

    @Test
    void aBonusWithoutATriggerIsAnError() {
        Diagnostics diags = new Diagnostics();
        String yaml = """
            armor:
              pieces:
                boots: { material: LEATHER_BOOTS }
            bonuses:
              - on: armor
                effects: [{ DAMAGE: { amount: 1 } }]
            """;
        SetDefReader.read("sets/x", root(yaml, diags), counter(), diags);
        assertCode(diags, DiagCode.E_LOAD_SET_TRIGGER);
    }

    @Test
    void enchantsBlockParsesToTheRefLevelMapPreservingAuthoredOrder() {
        Diagnostics diags = new Diagnostics();
        String yaml = """
            armor:
              enchants:
                enchants/frost: 2
                PROTECTION: 4
              pieces:
                boots: { material: DIAMOND_BOOTS }
            weapon:
              enchants:
                SHARPNESS: 5
              material: DIAMOND_SWORD
            bonuses:
              - on: armor
                trigger: DEFEND
                effects: [{ DAMAGE: { amount: 1 } }]
              - on: weapon
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
              pieces:
                boots: { material: DIAMOND_BOOTS }
            bonuses:
              - on: armor
                trigger: DEFEND
                effects: [{ DAMAGE: { amount: 1 } }]
            """;
        SetDefReader.Parsed parsed = SetDefReader.read("sets/frost", root(yaml, diags), counter(), diags);

        assertFalse(diags.hasErrors(), "a non-numeric level is a warning, not a blocking error");
        assertCode(diags, DiagCode.W_SET_ENCHANT);
        // the unparseable entry is dropped; its valid sibling survives
        assertEquals(Map.of("PROTECTION", 4), parsed.def().armorEnchants());
    }

    @Test
    void nonMappingFileIsAnErrorAndYieldsNoBonus() {
        Diagnostics diags = new Diagnostics();
        SetDefReader.Parsed parsed = SetDefReader.read("sets/x", root("- a\n- b\n", diags), counter(), diags);
        assertCode(diags, DiagCode.E_LOAD_SET);
        assertNull(parsed.def());
        assertTrue(parsed.abilities().isEmpty());
    }
}
