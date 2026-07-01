package item.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pure tests for the crystal-item data model (§E, ADR-0034) — no Bukkit. Pins the {@code "a+b+c"} entry
 * encoding, the {@code componentsOf} split (the same the WornResolver/LoreRenderer use), the capped N-way
 * merge with cursor-on-top order, and the empty/absolute-max component invariant.
 */
final class CrystalItemDataTest {

    @Test
    void singleEncodesAsThePlainKey() {
        CrystalItemData single = CrystalItemData.single("crystals/jolt");
        assertEquals("crystals/jolt", single.entry());
        assertFalse(single.isMulti());
    }

    @Test
    void multiEncodesWithDelimiterInOrder() {
        CrystalItemData multi = new CrystalItemData(List.of("crystals/jolt", "crystals/frost", "crystals/flame"));
        assertEquals("crystals/jolt+crystals/frost+crystals/flame", multi.entry());
        assertTrue(multi.isMulti());
    }

    @Test
    void componentsOfSplitsEntries() {
        assertEquals(List.of("crystals/jolt"), CrystalItemData.componentsOf("crystals/jolt"));
        assertEquals(List.of("crystals/jolt", "crystals/frost", "crystals/flame"),
                CrystalItemData.componentsOf("crystals/jolt+crystals/frost+crystals/flame"));
        assertEquals(List.of(), CrystalItemData.componentsOf(null));
        assertEquals(List.of(), CrystalItemData.componentsOf("  "));
    }

    @Test
    void mergePutsTheCursorOnTopAndHonoursTheCap() {
        CrystalItemData target = CrystalItemData.single("crystals/jolt");
        CrystalItemData cursor = CrystalItemData.single("crystals/frost");
        // cursor lands LAST (topmost) so the extractor pops the most-recently-merged crystal first.
        CrystalItemData merged = target.mergeWith(cursor, 2);
        assertEquals(List.of("crystals/jolt", "crystals/frost"), merged.keys());
        // A third component would exceed the cap of 2 → rejected (null), in either direction.
        assertNull(merged.mergeWith(CrystalItemData.single("crystals/flame"), 2));
        // Raising the cap admits it, appending on top.
        assertEquals(List.of("crystals/jolt", "crystals/frost", "crystals/flame"),
                merged.mergeWith(CrystalItemData.single("crystals/flame"), 3).keys());
    }

    @Test
    void rejectsEmptyOrPastAbsoluteMax() {
        assertThrows(IllegalArgumentException.class, () -> new CrystalItemData(List.of()));
        List<String> tooMany = java.util.stream.IntStream.rangeClosed(0, CrystalItemData.ABSOLUTE_MAX)
                .mapToObj(i -> "k" + i).toList(); // ABSOLUTE_MAX + 1 keys
        assertThrows(IllegalArgumentException.class, () -> new CrystalItemData(tooMany));
    }
}
