package feature.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import feature.menu.EnchantMenu.Click;
import feature.menu.EnchantMenu.ClickKind;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for {@link EnchantMenu#resolveClick} — the pagination/bounds decision behind the GUI,
 * exercised without a server (the live MenuSuite covers a single-page apply end-to-end, but pagination
 * needs &gt;45 enchants, so the wrap/empty-slot edges are pinned here). The slot constants: content slots
 * 0..44, PREV at 45, NEXT at 53; one page holds 45 enchants.
 */
class EnchantMenuPaginationTest {

    private static final int PREV = 45;
    private static final int NEXT = 53;

    @Test
    void contentSlotMapsToTheCatalogIndexForThePage() {
        assertEquals(new Click(ClickKind.APPLY, 0), EnchantMenu.resolveClick(0, 0, 50));
        assertEquals(new Click(ClickKind.APPLY, 44), EnchantMenu.resolveClick(0, 44, 50));
        assertEquals(new Click(ClickKind.APPLY, 45), EnchantMenu.resolveClick(1, 0, 50)); // page 1, slot 0
        assertEquals(new Click(ClickKind.APPLY, 49), EnchantMenu.resolveClick(1, 4, 50)); // page 1, slot 4 → index 49
    }

    @Test
    void aTrailingEmptyContentSlotOnTheLastPageIsIgnored() {
        // 50 enchants ⇒ page 1 holds indices 45..49; slot 5 would be index 50 (past the end).
        assertEquals(new Click(ClickKind.NONE, 0), EnchantMenu.resolveClick(1, 5, 50));
    }

    @Test
    void nextPaginatesOnlyWhenMorePagesRemain() {
        assertEquals(new Click(ClickKind.OPEN_PAGE, 1), EnchantMenu.resolveClick(0, NEXT, 50)); // 2 pages
        assertEquals(new Click(ClickKind.NONE, 0), EnchantMenu.resolveClick(1, NEXT, 50)); // last page → no wrap
        assertEquals(new Click(ClickKind.NONE, 0), EnchantMenu.resolveClick(0, NEXT, 10)); // single page → no wrap
    }

    @Test
    void prevPaginatesOnlyWhenNotOnTheFirstPage() {
        assertEquals(new Click(ClickKind.OPEN_PAGE, 0), EnchantMenu.resolveClick(1, PREV, 50));
        assertEquals(new Click(ClickKind.NONE, 0), EnchantMenu.resolveClick(0, PREV, 50)); // first page → no wrap
    }

    @Test
    void clicksOutsideTheWindowAreIgnored() {
        assertEquals(new Click(ClickKind.NONE, 0), EnchantMenu.resolveClick(0, -999, 50)); // click outside the GUI
        assertEquals(new Click(ClickKind.NONE, 0), EnchantMenu.resolveClick(0, 46, 50)); // bottom-row, not a nav slot
    }
}
