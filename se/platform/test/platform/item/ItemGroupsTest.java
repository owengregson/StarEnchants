package platform.item;

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
}
