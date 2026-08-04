package item.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The composite mask's on-item packing (ADR-0074) — the Multi Crystal shape, and its backward compatibility is
 * the point: a helmet stamped before composites existed carries a plain key, which must still read back as the
 * one mask it always was.
 */
class MaskItemDataTest {

    @Test
    void aPlainMaskEncodesAsTheBareKeyAndStaysReadable() {
        MaskItemData single = MaskItemData.single("masks/midas");
        assertEquals("masks/midas", single.entry(), "no delimiter, so a pre-composite helmet decodes unchanged");
        assertFalse(single.isMulti());
        assertEquals("masks/midas", single.first());
        assertEquals(List.of("masks/midas"), MaskItemData.componentsOf("masks/midas"));
    }

    @Test
    void aCompositeEncodesItsChildrenInFoldOrder() {
        MaskItemData folded = new MaskItemData(List.of("masks/agent", "masks/blaze", "masks/midas"));
        assertEquals("masks/agent+masks/blaze+masks/midas", folded.entry());
        assertTrue(folded.isMulti());
        // The FIRST child is the face the illusion shows and the colour the compound name takes.
        assertEquals("masks/agent", folded.first());
        assertEquals(3, MaskItemData.componentsOf(folded.entry()).size());
    }

    @Test
    void componentsOfToleratesAbsentAndMalformedEntries() {
        assertTrue(MaskItemData.componentsOf(null).isEmpty());
        assertTrue(MaskItemData.componentsOf("   ").isEmpty());
        assertTrue(MaskItemData.componentsOf("+").isEmpty(), "nothing but delimiters is no mask, never a throw");
        assertEquals(List.of("masks/a", "masks/b"), MaskItemData.componentsOf("masks/a++masks/b"));
    }

    @Test
    void aFoldPutsTheCursorOnTopAndHonoursTheCap() {
        MaskItemData target = MaskItemData.single("masks/agent");
        MaskItemData cursor = MaskItemData.single("masks/blaze");

        MaskItemData folded = target.mergeWith(cursor, 2);
        // Cursor lands LAST: the extractor pops the most recently folded child first, and the target keeps the
        // face — folding B onto A must not silently repaint the wearer as B.
        assertEquals(List.of("masks/agent", "masks/blaze"), folded.keys());
        assertEquals("masks/agent", folded.first());

        assertNull(folded.mergeWith(MaskItemData.single("masks/midas"), 2), "a third past the cap is refused whole");
        assertEquals(3, folded.mergeWith(MaskItemData.single("masks/midas"), 3).keys().size(),
                "raising masks.max-merge admits it, live");
        assertNull(folded.mergeWith(null, 4));
    }

    @Test
    void aCapOfOneRefusesEveryFold() {
        // masks.max-merge: 1 is the documented "no folding at all" setting — masks behave exactly as they did
        // before composites existed, and the gesture refuses rather than silently doing nothing.
        assertNull(MaskItemData.single("masks/agent").mergeWith(MaskItemData.single("masks/blaze"), 1));
    }

    @Test
    void rejectsEmptyOrPastTheAbsoluteMax() {
        assertThrows(IllegalArgumentException.class, () -> new MaskItemData(List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new MaskItemData(java.util.Collections.nCopies(MaskItemData.ABSOLUTE_MAX + 1, "masks/a")));
    }

    @Test
    void theTwoFamiliesShareOneDelimiter() {
        // If these ever diverged, a crystal entry would decode as one mask key (or vice versa) the moment either
        // string reached the other's splitter — and both splitters run over strings read from the same blob.
        assertEquals(CrystalItemData.DELIMITER, MaskItemData.DELIMITER);
        assertEquals(CrystalItemData.ABSOLUTE_MAX, MaskItemData.ABSOLUTE_MAX);
    }
}
