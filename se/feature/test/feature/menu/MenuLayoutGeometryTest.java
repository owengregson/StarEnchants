package feature.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The frame geometry (ADR-0030): {@link MenuLayout#contentSlot}/{@link MenuLayout#contentSlotCount}/
 * {@link MenuLayout#paneSlots}. A transposition in the BORDER inset math (row/col swap, off-by-one perimeter)
 * is a real bug the live suite — which reads {@code contentSlot(0)} and so would happily click the wrong-but-
 * consistent cell — cannot catch. Pure arithmetic, server-free.
 */
class MenuLayoutGeometryTest {

    @Test
    void borderInsetsContentInsideAOneCellPerimeter() {
        MenuLayout border = MenuLayout.paged("X"); // 6 rows, Frame.BORDER
        assertEquals(28, border.contentSlotCount());     // 4 interior rows × 7 inner columns
        assertEquals(10, border.contentSlot(0));         // row 1, col 1 — never slot 0 (that is the top border)
        assertEquals(16, border.contentSlot(6));         // end of the first interior row
        assertEquals(19, border.contentSlot(7));         // wraps to row 2, col 1
        assertEquals(43, border.contentSlot(27));        // last interior cell
        assertEquals(-1, border.contentSlot(28));        // out of range
        // No content cell is also a frame pane, and the perimeter is fully panelled.
        List<Integer> panes = border.paneSlots();
        assertEquals(26, panes.size());                  // top 9 + bottom 9 + 4 rows × 2 sides
        assertTrue(panes.containsAll(List.of(0, 8, 9, 17, 45, 53)));
        assertFalse(panes.contains(10));                 // a content cell is never a pane
        assertFalse(panes.contains(43));
    }

    @Test
    void bottomAndNoneFillEveryCellAboveTheNavRow() {
        MenuLayout bottom = new MenuLayout(6, "X", "GRAY_STAINED_GLASS_PANE", Frame.BOTTOM, 45, 53, 48, 49);
        assertEquals(45, bottom.contentSlotCount());
        assertEquals(0, bottom.contentSlot(0));
        assertEquals(44, bottom.contentSlot(44));
        assertEquals(-1, bottom.contentSlot(45));
        assertEquals(9, bottom.paneSlots().size());      // only the reserved nav row

        MenuLayout none = new MenuLayout(6, "X", "", Frame.NONE, -1, -1, -1, 53);
        assertEquals(45, none.contentSlotCount());
        assertEquals(0, none.contentSlot(0));
        assertTrue(none.paneSlots().isEmpty());          // no decorative panes at all
    }
}
