package item.mint;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

/**
 * Pure tests for the cross-version material resolver — no running server. Pins exact-name and
 * case-insensitive resolution, and the never-null fallback for an unknown/blank token (so a bad
 * config material can never crash a mint).
 */
final class ItemFactoryTest {

    @Test
    void resolvesExactName() {
        assertEquals(Material.EMERALD, ItemFactory.material("EMERALD", Material.PAPER));
    }

    @Test
    void resolvesCaseInsensitively() {
        assertEquals(Material.EMERALD, ItemFactory.material("emerald", Material.PAPER));
        assertEquals(Material.DIAMOND_SWORD, ItemFactory.material(" Diamond_Sword ", Material.PAPER));
    }

    @Test
    void fallsBackOnUnknown() {
        assertEquals(Material.PAPER, ItemFactory.material("NOT_A_REAL_MATERIAL", Material.PAPER));
    }

    @Test
    void fallsBackOnBlankOrNull() {
        assertEquals(Material.STONE, ItemFactory.material("   ", Material.STONE));
        assertEquals(Material.STONE, ItemFactory.material(null, Material.STONE));
    }
}
