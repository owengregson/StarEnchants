package feature.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for {@link Paging} — the pagination arithmetic behind every paged menu, exercised without
 * a server. The live suite proves render + click routing end-to-end, but the page-count / clamp / index
 * edges (overflow, wrap, last-page tail) are pinned here. A standard 6-row paged layout has 45 content
 * slots ({@code (6-1)*9}).
 */
class PagingTest {

    private static final int PER_PAGE = 45;

    @Test
    void pageCountRoundsUpAndIsAtLeastOne() {
        assertEquals(1, Paging.pageCount(0, PER_PAGE));   // empty catalog still shows one (blank) page
        assertEquals(1, Paging.pageCount(45, PER_PAGE));  // exactly full → one page, not two
        assertEquals(2, Paging.pageCount(46, PER_PAGE));  // one over → a second page
        assertEquals(3, Paging.pageCount(100, PER_PAGE));
        assertEquals(1, Paging.pageCount(10, 0));         // a degenerate zero-per-page never divides by zero
    }

    @Test
    void clampWrapsAStaleOrOverLargePageIntoRange() {
        assertEquals(0, Paging.clampPage(0, 3));
        assertEquals(2, Paging.clampPage(2, 3));
        assertEquals(0, Paging.clampPage(3, 3));   // one past the end wraps to the first page
        assertEquals(1, Paging.clampPage(-2, 3));  // negative wraps via floorMod, never throws
        assertEquals(0, Paging.clampPage(5, 0));   // no pages → clamp to 0
    }

    @Test
    void indexForMapsPageAndSlotToTheCatalogIndex() {
        assertEquals(0, Paging.indexFor(0, 0, PER_PAGE));
        assertEquals(44, Paging.indexFor(0, 44, PER_PAGE));
        assertEquals(45, Paging.indexFor(1, 0, PER_PAGE));  // page 1, slot 0
        assertEquals(49, Paging.indexFor(1, 4, PER_PAGE));  // page 1, slot 4 → index 49
    }
}
