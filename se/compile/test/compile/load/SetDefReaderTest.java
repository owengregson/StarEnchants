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
        // ref→roll, authored order preserved — it determines the minted piece's enchant lore order
        assertEquals(List.of("enchants/frost", "PROTECTION"), List.copyOf(parsed.def().armorEnchants().keySet()));
        assertEquals(EnchantRoll.fixed(2), parsed.def().armorEnchants().get("enchants/frost"));
        assertEquals(Map.of("SHARPNESS", EnchantRoll.fixed(5)), parsed.def().weaponEnchants());
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
        assertEquals(Map.of("PROTECTION", EnchantRoll.fixed(4)), parsed.def().armorEnchants());
    }

    @Test
    void aPieceCarriesItsOwnLoreEnchantsDyeAndHeroicStamp() {
        Diagnostics diags = new Diagnostics();
        String yaml = """
            armor:
              lore: ["&7SET BONUS"]
              enchants:
                PROTECTION: 4
              pieces:
                helmet: { material: DIAMOND_HELMET }
                boots:
                  material: LEATHER_BOOTS
                  name: "&3Whisp"
                  color: "#808080"
                  heroic: true
                  lore: ["&7&oNo feet."]
                  enchants:
                    enchants/gears: 3
            bonuses:
              - on: armor
                trigger: DEFEND
                effects: [{ DAMAGE: { amount: 1 } }]
            """;
        SetDefReader.Parsed parsed = SetDefReader.read("sets/ghost", root(yaml, diags), counter(), diags);

        assertFalse(diags.hasErrors(), () -> diags.all().toString());
        SetDef.Member boots = parsed.def().armorMember("boots");
        assertEquals("#808080", boots.color());
        assertTrue(boots.heroic());
        assertEquals(List.of("&7&oNo feet."), boots.lore());
        assertEquals(Map.of("enchants/gears", EnchantRoll.fixed(3)), boots.enchants());
        // a slot that says nothing of its own keeps every default — the shape every pre-existing set has
        SetDef.Member helmet = parsed.def().armorMember("helmet");
        assertNull(helmet.color());
        assertFalse(helmet.heroic());
        assertTrue(helmet.lore().isEmpty());
        assertTrue(helmet.enchants().isEmpty());
    }

    @Test
    void perPieceLoreAndEnchantsRefineTheSharedOnesRatherThanReplacingThem() {
        Diagnostics diags = new Diagnostics();
        String yaml = """
            armor:
              lore: ["&7SET BONUS", "&7* Something"]
              enchants:
                PROTECTION: 4
                UNBREAKING: 3
              pieces:
                helmet: { material: DIAMOND_HELMET }
                boots:
                  material: LEATHER_BOOTS
                  lore: ["&7&oFlavour."]
                  enchants:
                    PROTECTION: 5
                    enchants/gears: 3
            bonuses:
              - on: armor
                trigger: DEFEND
                effects: [{ DAMAGE: { amount: 1 } }]
            """;
        SetDef def = SetDefReader.read("sets/ghost", root(yaml, diags), counter(), diags).def();

        assertFalse(diags.hasErrors(), () -> diags.all().toString());
        // the piece's own flavour prints ABOVE the shared block, never instead of it
        assertEquals(List.of("&7&oFlavour.", "&7SET BONUS", "&7* Something"), def.armorLoreFor("boots"));
        // a slot with nothing of its own, and an unknown token, both read exactly the shared block
        assertEquals(def.armorLore(), def.armorLoreFor("helmet"));
        assertEquals(def.armorLore(), def.armorLoreFor("ELYTRA"));
        assertEquals(def.armorLore(), def.armorLoreFor(null));
        // the shared roster first, in authored order, with the piece's own entries applied over it
        assertEquals(List.of("PROTECTION", "UNBREAKING", "enchants/gears"),
                List.copyOf(def.armorEnchantsFor("boots").keySet()));
        assertEquals(EnchantRoll.fixed(5), def.armorEnchantsFor("boots").get("PROTECTION"));
        assertEquals(def.armorEnchants(), def.armorEnchantsFor("helmet"));
    }

    @Test
    void theSlotTokenIsMatchedCaseInsensitivelySoAGearKindResolvesIt() {
        Diagnostics diags = new Diagnostics();
        String yaml = """
            armor:
              pieces:
                boots: { material: LEATHER_BOOTS, lore: ["&7&oFlavour."] }
            bonuses:
              - on: armor
                trigger: DEFEND
                effects: [{ DAMAGE: { amount: 1 } }]
            """;
        SetDef def = SetDefReader.read("sets/ghost", root(yaml, diags), counter(), diags).def();

        // LoreRenderer hands the composer a material kind (LEATHER_BOOTS -> "BOOTS"), not the authored token
        assertEquals(List.of("&7&oFlavour."), def.armorLoreFor("BOOTS"));
        assertEquals(def.armorMember("boots"), def.armorMember("BOOTS"));
    }

    @Test
    void aSetMayDeclareSeveralWeaponsAndTheSingularFormStaysTheFirstOne() {
        // R-QC35a: `weapons:` is the keyed form of `weapon:` — the same shape `armor.pieces` uses. Every read
        // written against the singular form must keep answering, which is why weapon() is the FIRST member.
        Diagnostics diags = new Diagnostics();
        String yaml = """
            armor:
              pieces:
                helmet: { material: DIAMOND_HELMET }
            weapons:
              sword:
                material: DIAMOND_SWORD
                name: "KOTH Sword"
              axe:
                material: DIAMOND_AXE
                lore: ["&7strips armour"]
                enchants:
                  SHARPNESS: 5
            bonuses:
              - on: armor
                trigger: DEFEND
                effects: [{ DAMAGE: { amount: 1 } }]
            """;
        SetDef def = SetDefReader.read("sets/koth", root(yaml, diags), counter(), diags).def();

        assertFalse(diags.hasErrors(), () -> diags.all().toString());
        assertTrue(def.hasWeapon());
        assertEquals(2, def.weaponMembers().size());
        assertEquals("DIAMOND_SWORD", def.weapon().material(), "the singular read is the FIRST weapon");
        assertEquals("DIAMOND_AXE", def.weaponMember("axe").material());
        assertNull(def.weaponMember("mace"), "an undeclared token resolves to nothing rather than the first");
        // Lore and roster live on the MEMBER now — a weapon has no shared block to refine, which is exactly
        // what lets several of them coexist under one def.
        assertEquals(List.of("&7strips armour"), def.weaponLoreFor("axe"));
        assertEquals(List.of(), def.weaponLoreFor("sword"));
        assertEquals(List.of(), def.weaponLoreFor("mace"), "an unknown token falls back to the first weapon's");
        assertEquals(Map.of("SHARPNESS", EnchantRoll.fixed(5)), def.weaponMember("axe").enchants());
        assertEquals(Map.of(), def.weaponEnchants(), "the singular roster is the first weapon's");
    }

    @Test
    void theClaimFooterParsesAsTwoFormsAndIsEmptyWhenUnauthored() {
        // R-QC35c: the unclaimed line is a DIFFERENT sentence, not the claimed one with an empty token, so
        // the pair is authored as a pair. A set that stakes nothing must read empty — every set but KOTH.
        Diagnostics diags = new Diagnostics();
        String staked = """
            armor:
              pieces:
                helmet: { material: DIAMOND_HELMET }
            claim-footer:
              claimed: "Claimed by {CLAIMANT} on {DATE}"
              unclaimed: "Claimed on {DATE}"
            bonuses:
              - on: armor
                trigger: DEFEND
                effects: [{ DAMAGE: { amount: 1 } }]
            """;
        String plain = """
            armor:
              pieces:
                helmet: { material: DIAMOND_HELMET }
            bonuses:
              - on: armor
                trigger: DEFEND
                effects: [{ DAMAGE: { amount: 1 } }]
            """;
        SetDef koth = SetDefReader.read("sets/koth", root(staked, diags), counter(), diags).def();
        SetDef yeti = SetDefReader.read("sets/yeti", root(plain, diags), counter(), diags).def();

        assertFalse(diags.hasErrors(), () -> diags.all().toString());
        assertEquals("Claimed by {CLAIMANT} on {DATE}", koth.claimFooter().claimed());
        assertEquals("Claimed on {DATE}", koth.claimFooter().unclaimed());
        assertFalse(koth.claimFooter().isEmpty());
        assertTrue(yeti.claimFooter().isEmpty(), "a set that stakes nothing carries no footer");
    }

    @Test
    void theRollFormsParseToTheirBandsAndAnUnreadableOneWarnsAndIsSkipped() {
        Diagnostics diags = new Diagnostics();
        String yaml = """
            armor:
              enchants:
                enchants/band: { min: 2, max: 5 }
                enchants/near: { nearly-maxed: 4 }
                enchants/mkit: { ability-set: 4 }
                enchants/mkitgated: { chance: 17.5, ability-set: 3 }
                enchants/unreadable: { ability-set: wide }
                enchants/gated: { chance: 25, min: 1, max: 4 }
                enchants/half: { chance: 17.5, min: 1, max: 4 }
                enchants/one: { max: 3 }
                enchants/levelless: { chance: 30 }
              pieces:
                boots: { material: DIAMOND_BOOTS }
            bonuses:
              - on: armor
                trigger: DEFEND
                effects: [{ DAMAGE: { amount: 1 } }]
            """;
        SetDef def = SetDefReader.read("sets/x", root(yaml, diags), counter(), diags).def();

        assertFalse(diags.hasErrors(), "a malformed roll is a warning, not a blocking error");
        assertCode(diags, DiagCode.W_SET_ENCHANT);
        Map<String, EnchantRoll> roster = def.armorEnchants();
        assertEquals(new EnchantRoll(2, 5, 100, EnchantRoll.Mode.UNIFORM), roster.get("enchants/band"));
        // nearly-maxed fixes both bounds from M: max(1, M-2) .. M
        assertEquals(new EnchantRoll(2, 4, 100, EnchantRoll.Mode.NEARLY_MAXED), roster.get("enchants/near"));
        // R-QC64: the M-Kit draw opens a rung wider at the bottom, so its declared floor is max(1, M-3) — the
        // floor LibraryLoader prints, and the lowest level the draw can actually mint.
        assertEquals(new EnchantRoll(1, 4, 100, EnchantRoll.Mode.ABILITY_SET), roster.get("enchants/mkit"));
        assertEquals(new EnchantRoll(1, 3, 17.5, EnchantRoll.Mode.ABILITY_SET), roster.get("enchants/mkitgated"));
        // an unreadable M is dropped with a warning, exactly as an unreadable nearly-maxed is — never guessed,
        // and never silently falling through to the min/max branch that would read no level at all
        assertFalse(roster.containsKey("enchants/unreadable"));
        assertEquals(new EnchantRoll(1, 4, 25, EnchantRoll.Mode.UNIFORM), roster.get("enchants/gated"));
        // R-QC51: a half-point survives the reader. It used to fail Integer.valueOf, warn, and fall back to
        // 100 — turning the rarest entry in a roster into one that minted on every single piece.
        assertEquals(new EnchantRoll(1, 4, 17.5, EnchantRoll.Mode.UNIFORM), roster.get("enchants/half"));
        // a band of one is not a draw — it degrades to the fixed form so no RNG is consumed for it
        assertEquals(EnchantRoll.Mode.FIXED, roster.get("enchants/one").mode());
        assertEquals(3, roster.get("enchants/one").max());
        // a roll naming no level at all is dropped rather than guessed
        assertFalse(roster.containsKey("enchants/levelless"));
    }

    @Test
    void nonMappingFileIsAnErrorAndYieldsNoBonus() {
        Diagnostics diags = new Diagnostics();
        SetDefReader.Parsed parsed = SetDefReader.read("sets/x", root("- a\n- b\n", diags), counter(), diags);
        assertCode(diags, DiagCode.E_LOAD_SET);
        assertNull(parsed.def());
        assertTrue(parsed.abilities().isEmpty());
    }

    @Test
    void weaponBonusWithoutWeaponItemWarnsUnreachable() {
        Diagnostics diags = new Diagnostics();
        // Both bonuses carry effects so W_LOAD_EFFECTS cannot fire — the only warning must be the split code.
        String yaml = """
            complete: 1
            armor:
              pieces:
                helmet: { material: DIAMOND_HELMET }
            bonuses:
              - on: armor
                trigger: DEFEND
                effects: [{ DAMAGE: { amount: 2 } }]
              - on: weapon
                trigger: ATTACK
                effects: [{ HEAL: { amount: 1 } }]
            """;
        SetDefReader.read("sets/orphan-weapon", root(yaml, diags), counter(), diags);

        assertCode(diags, DiagCode.W_LOAD_SET_WEAPON_UNREACHABLE);
        assertFalse(diags.all().stream().anyMatch(d -> d.is(DiagCode.W_LOAD_EFFECTS)),
                () -> diags.all().toString());
        assertFalse(diags.hasErrors(), () -> diags.all().toString());
    }

    @Test
    void announceOutsideTheBoolVocabularyWarnsAndStaysOff() {
        Diagnostics diags = new Diagnostics();
        String yaml = """
            complete: 1
            announce: "sometimes"
            armor:
              pieces:
                helmet: { material: DIAMOND_HELMET }
            bonuses:
              - on: armor
                trigger: DEFEND
                effects: [{ DAMAGE: { amount: 2 } }]
            """;
        SetDefReader.Parsed parsed = SetDefReader.read("sets/announce", root(yaml, diags), counter(), diags);

        assertFalse(parsed.def().announce());
        assertCode(diags, DiagCode.W_LOAD_BOOL);
    }
}
