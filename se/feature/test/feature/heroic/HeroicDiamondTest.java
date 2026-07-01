package feature.heroic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

/** Pure tests for {@link HeroicDiamond} — the §F plugin-calculated diamond-equivalence maths, server-free. */
class HeroicDiamondTest {

    @Test
    void weaponFlatDamageRaisesAWeakWeaponToDiamond() {
        assertEquals(3.0, HeroicDiamond.weaponFlatDamage(Material.GOLDEN_SWORD)); // gold 4 → diamond 7
        assertEquals(2.0, HeroicDiamond.weaponFlatDamage(Material.GOLDEN_AXE));   // gold 7 → diamond 9
        assertEquals(0.0, HeroicDiamond.weaponFlatDamage(Material.DIAMOND_SWORD)); // already diamond → no bonus
        assertEquals(0.0, HeroicDiamond.weaponFlatDamage(Material.GOLDEN_HELMET)); // not a weapon
    }

    @Test
    void armourFlatReductionRaisesWeakArmourToDiamondAndIsZeroForDiamondOrNonArmour() {
        assertTrue(HeroicDiamond.armourFlatReduction(Material.GOLDEN_CHESTPLATE) > 0.0);
        assertEquals(0.0, HeroicDiamond.armourFlatReduction(Material.DIAMOND_CHESTPLATE)); // already diamond
        assertEquals(0.0, HeroicDiamond.armourFlatReduction(Material.GOLDEN_SWORD));        // not armour
    }

    @Test
    void diamondArmourPointsAndToughnessAreTheRealVanillaValuesPerSlot() {
        // The REAL vanilla armour points written as a GENERIC_ARMOR modifier when vanilla-stats is on (ADR-0031),
        // distinct from the flat-reduction approximation above. Diamond: helmet 3, chest 8, legs 6, boots 3; +2 toughness.
        assertEquals(3, HeroicDiamond.diamondArmourPoints(Material.GOLDEN_HELMET));
        assertEquals(8, HeroicDiamond.diamondArmourPoints(Material.GOLDEN_CHESTPLATE));
        assertEquals(6, HeroicDiamond.diamondArmourPoints(Material.GOLDEN_LEGGINGS));
        assertEquals(3, HeroicDiamond.diamondArmourPoints(Material.GOLDEN_BOOTS));
        assertEquals(0, HeroicDiamond.diamondArmourPoints(Material.GOLDEN_SWORD)); // not armour
        assertEquals(2, HeroicDiamond.diamondArmourToughness(Material.GOLDEN_CHESTPLATE));
        assertEquals(0, HeroicDiamond.diamondArmourToughness(Material.GOLDEN_SWORD));
    }

    @Test
    void diamondAttackDamageIsTheRealVanillaWeaponTotalInclBase() {
        // Total incl. the player base 1.0, as the "N Attack Damage" tooltip shows; the written modifier is this − 1.
        assertEquals(7.0, HeroicDiamond.diamondAttackDamage(Material.GOLDEN_SWORD));   // diamond sword 7
        assertEquals(9.0, HeroicDiamond.diamondAttackDamage(Material.GOLDEN_AXE));     // diamond axe 9
        assertEquals(7.0, HeroicDiamond.diamondAttackDamage(Material.DIAMOND_SWORD));
        assertEquals(0.0, HeroicDiamond.diamondAttackDamage(Material.GOLDEN_HELMET));  // not a weapon
    }

    @Test
    void diamondMaterialNameIsTheStandInForSubDiamondGearElseNull() {
        assertEquals("DIAMOND_SWORD", HeroicDiamond.diamondMaterialName(Material.GOLDEN_SWORD));
        assertEquals("DIAMOND_AXE", HeroicDiamond.diamondMaterialName(Material.GOLDEN_AXE));
        assertEquals("DIAMOND_CHESTPLATE", HeroicDiamond.diamondMaterialName(Material.IRON_CHESTPLATE));
        assertEquals("DIAMOND_BOOTS", HeroicDiamond.diamondMaterialName(Material.CHAINMAIL_BOOTS));
        assertNull(HeroicDiamond.diamondMaterialName(Material.DIAMOND_BOOTS));    // already diamond
        assertNull(HeroicDiamond.diamondMaterialName(Material.NETHERITE_HELMET)); // already ≥ diamond
        assertNull(HeroicDiamond.diamondMaterialName(Material.STICK));            // not gear
    }

    @Test
    void displayBelowDiamondArmourFlagsSubDiamondArmourOnly() {
        assertTrue(HeroicDiamond.displayBelowDiamondArmour(Material.GOLDEN_CHESTPLATE));
        assertTrue(HeroicDiamond.displayBelowDiamondArmour(Material.IRON_BOOTS));
        assertFalse(HeroicDiamond.displayBelowDiamondArmour(Material.DIAMOND_CHESTPLATE)); // already diamond
        assertFalse(HeroicDiamond.displayBelowDiamondArmour(Material.NETHERITE_HELMET));   // already ≥ diamond
        assertFalse(HeroicDiamond.displayBelowDiamondArmour(Material.GOLDEN_SWORD));        // not armour
    }

    @Test
    void scaledWearCancelMakesSubDiamondPiecesLastLikeDiamondButLeavesDurableOnesAlone() {
        double base = 0.20;
        // A gold sword (max 32) vs diamond (1561) cancels almost all wear so it survives ~diamond hits.
        double gold = HeroicDiamond.scaledWearCancel(32, Material.GOLDEN_SWORD, base);
        assertTrue(gold > 0.97 && gold <= 1.0, "gold should cancel almost all wear, got " + gold);
        // A material already at/above diamond durability keeps the base 80%-wear chance (no scaling).
        assertEquals(base, HeroicDiamond.scaledWearCancel(2031, Material.NETHERITE_SWORD, base));
        // Non-gear (no diamond max) also keeps the base chance.
        assertEquals(base, HeroicDiamond.scaledWearCancel(0, Material.STICK, base));
    }
}
