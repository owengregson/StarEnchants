package platform.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

/**
 * Pure membership tests for the built-in item groups. {@link Material} is a plain enum on the
 * paper-api floor, so this needs no server.
 */
class ItemGroupsTest {

    private final ItemGroups groups = ItemGroups.standard();

    @Test
    void swordIsAWeaponNotArmor() {
        assertTrue(groups.matches(Material.DIAMOND_SWORD, List.of("SWORD")));
        assertTrue(groups.matches(Material.DIAMOND_SWORD, List.of("WEAPON")));
        assertFalse(groups.matches(Material.DIAMOND_SWORD, List.of("ARMOR")));
    }

    @Test
    void helmetIsInHelmetAndArmor() {
        assertTrue(groups.matches(Material.DIAMOND_HELMET, List.of("HELMET")));
        assertTrue(groups.matches(Material.DIAMOND_HELMET, List.of("ARMOR")));
        assertTrue(groups.matches(Material.TURTLE_HELMET, List.of("HELMET")));
        assertFalse(groups.matches(Material.DIAMOND_HELMET, List.of("WEAPON")));
    }

    @Test
    void axeIsBothWeaponAndTool() {
        assertTrue(groups.matches(Material.IRON_AXE, List.of("WEAPON")));
        assertTrue(groups.matches(Material.IRON_AXE, List.of("TOOL")));
    }

    @Test
    void wildcardMatchesAnyItemButNotAir() {
        assertTrue(groups.matches(Material.STICK, List.of("ALL")));
        assertFalse(groups.matches(Material.AIR, List.of("ALL")));
    }

    @Test
    void tokensAreCaseInsensitiveAndUnknownTokensMiss() {
        assertTrue(groups.matches(Material.IRON_AXE, List.of("axe")));
        assertFalse(groups.matches(Material.DIAMOND_SWORD, List.of("BANANA")));
        assertFalse(groups.matches(Material.DIAMOND_SWORD, List.of()));
    }

    @Test
    void kindsLabelTitleCasesAndSeriallyJoinsTokens() {
        assertEquals("", ItemGroups.kindsLabel(List.of()));
        assertEquals("Sword", ItemGroups.kindsLabel(List.of("SWORD")));
        // a non-collapsing pair still serial-joins
        assertEquals("Sword & Bow", ItemGroups.kindsLabel(List.of("SWORD", "BOW")));
        // 3 of 4 armour slots is NOT the full set → serial join (Oxford comma before the final item)
        assertEquals("Boots, Leggings, & Helmet", ItemGroups.kindsLabel(List.of("BOOTS", "LEGGINGS", "HELMET")));
    }

    @Test
    void kindsLabelCollapsesRecognisedGroups() {
        // the four armour slots → "Armor" (order-independent)
        assertEquals("Armor", ItemGroups.kindsLabel(List.of("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS")));
        assertEquals("Armor", ItemGroups.kindsLabel(List.of("BOOTS", "HELMET", "LEGGINGS", "CHESTPLATE")));
        // sword + axe → "Weapon"
        assertEquals("Weapon", ItemGroups.kindsLabel(List.of("SWORD", "AXE")));
        // a lone fishing rod → "Rod" (not "Fishing Rod")
        assertEquals("Rod", ItemGroups.kindsLabel(List.of("FISHING_ROD")));
    }
}
