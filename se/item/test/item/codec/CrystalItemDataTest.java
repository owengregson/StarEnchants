package item.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pure tests for the crystal-item data model (§E) — no Bukkit. Pins the {@code "a+b"} entry encoding,
 * the {@code componentsOf} split (the same the WornResolver/LoreRenderer use), the pairs-only merge,
 * and the 1..2 component invariant.
 */
final class CrystalItemDataTest {

    @Test
    void singleEncodesAsThePlainKey() {
        CrystalItemData single = CrystalItemData.single("crystals/jolt");
        assertEquals("crystals/jolt", single.entry());
        assertFalse(single.isMulti());
    }

    @Test
    void multiEncodesWithDelimiter() {
        CrystalItemData multi = new CrystalItemData(List.of("crystals/jolt", "crystals/frost"));
        assertEquals("crystals/jolt+crystals/frost", multi.entry());
        assertTrue(multi.isMulti());
    }

    @Test
    void componentsOfSplitsEntries() {
        assertEquals(List.of("crystals/jolt"), CrystalItemData.componentsOf("crystals/jolt"));
        assertEquals(List.of("crystals/jolt", "crystals/frost"),
                CrystalItemData.componentsOf("crystals/jolt+crystals/frost"));
        assertEquals(List.of(), CrystalItemData.componentsOf(null));
        assertEquals(List.of(), CrystalItemData.componentsOf("  "));
    }

    @Test
    void mergePairsOnly() {
        CrystalItemData a = CrystalItemData.single("crystals/jolt");
        CrystalItemData b = CrystalItemData.single("crystals/frost");
        CrystalItemData merged = a.mergeWith(b);
        assertEquals(List.of("crystals/jolt", "crystals/frost"), merged.keys());
        // a multi cannot merge further (pairs only), in either direction
        assertNull(merged.mergeWith(a));
        assertNull(a.mergeWith(merged));
    }

    @Test
    void rejectsEmptyOrTooMany() {
        assertThrows(IllegalArgumentException.class, () -> new CrystalItemData(List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new CrystalItemData(List.of("a", "b", "c")));
    }
}
