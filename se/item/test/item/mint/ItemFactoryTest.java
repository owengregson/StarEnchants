package item.mint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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

    @Test
    void legacyFallbackMapsNewerMaterialsToOlderEquivalents() {
        // The newer→older degradation table for the optional 1.8 lane (shovels are _SPADE on 1.8).
        assertEquals("DIAMOND_HELMET", ItemFactory.legacyFallback("NETHERITE_HELMET"));
        assertEquals("DIAMOND_SWORD", ItemFactory.legacyFallback("NETHERITE_SWORD"));
        assertEquals("DIAMOND_SPADE", ItemFactory.legacyFallback("NETHERITE_SHOVEL"));
        assertNull(ItemFactory.legacyFallback("STONE"));
    }

    @Test
    void legacyFallbackCoversTheUseItemLikenessMaterials() {
        // Godly Transmog is WRITABLE_BOOK (1.13 rename → BOOK_AND_QUILL on 1.8); RED_DYE has no by-name 1.8
        // equivalent (real red dye was INK_SACK+data), so it degrades to REDSTONE (a red item that exists).
        assertEquals("BOOK_AND_QUILL", ItemFactory.legacyFallback("WRITABLE_BOOK"));
        assertEquals("REDSTONE", ItemFactory.legacyFallback("RED_DYE"));
        // The cosmic pack's two recovered dye-family materials: INK_SAC is a plain rename, YELLOW_DYE is the
        // same data-value problem RED_DYE has and degrades to the closest 1.8 yellow item.
        assertEquals("INK_SACK", ItemFactory.legacyFallback("INK_SAC"));
        assertEquals("GOLD_INGOT", ItemFactory.legacyFallback("YELLOW_DYE"));
    }
}
