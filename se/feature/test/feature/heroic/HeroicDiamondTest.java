package feature.heroic;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
