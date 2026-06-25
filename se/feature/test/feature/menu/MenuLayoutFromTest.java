package feature.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import compile.load.MenuLayoutConfig;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/** The §L merge of a menus/<name>.yml override onto a programmatic MenuLayout default (MenuLayout.from). */
class MenuLayoutFromTest {

    private static MenuLayoutConfig of(OptionalInt rows, Optional<String> title, Optional<String> filler,
                                       OptionalInt prev, OptionalInt next, OptionalInt back, OptionalInt close) {
        return new MenuLayoutConfig(rows, title, filler, prev, next, back, close);
    }

    @Test
    void nullOverrideReturnsTheDefaultUnchanged() {
        MenuLayout def = MenuLayout.paged("Base");
        assertSame(def, MenuLayout.from(def, null));
    }

    @Test
    void presentFieldsWinAndAbsentFieldsKeepTheDefault() {
        MenuLayout def = MenuLayout.paged("Base"); // 6 rows, prev45 next53 back48 close49, GRAY filler
        MenuLayout merged = MenuLayout.from(def, of(
                OptionalInt.empty(), Optional.of("&aCustom"), Optional.of("RED_STAINED_GLASS_PANE"),
                OptionalInt.empty(), OptionalInt.empty(), OptionalInt.empty(), OptionalInt.empty()));

        assertEquals("&aCustom", merged.titleTemplate());
        assertEquals("RED_STAINED_GLASS_PANE", merged.fillerMaterial());
        assertEquals(6, merged.rows());                            // absent override keeps the default
        assertEquals(45, merged.prevSlot());
        assertEquals(53, merged.nextSlot());
        assertEquals(49, merged.closeSlot());
    }

    @Test
    void shrinkingRowsHidesNavSlotsThatNoLongerFit() {
        MenuLayout def = MenuLayout.paged("Base"); // close=49 etc., size 54
        MenuLayout merged = MenuLayout.from(def, of(
                OptionalInt.of(3), Optional.empty(), Optional.empty(),
                OptionalInt.empty(), OptionalInt.empty(), OptionalInt.empty(), OptionalInt.empty()));

        assertEquals(3, merged.rows());      // size now 27
        assertEquals(-1, merged.prevSlot()); // 45 ≥ 27 → hidden, not an out-of-bounds crash
        assertEquals(-1, merged.nextSlot()); // 53 ≥ 27 → hidden
        assertEquals(-1, merged.closeSlot()); // 49 ≥ 27 → hidden
    }

    @Test
    void explicitSlotsWithinRangeAreApplied() {
        MenuLayout def = MenuLayout.paged("Base");
        MenuLayout merged = MenuLayout.from(def, of(
                OptionalInt.of(3), Optional.empty(), Optional.empty(),
                OptionalInt.of(18), OptionalInt.of(26), OptionalInt.of(-1), OptionalInt.of(22)));

        assertEquals(18, merged.prevSlot());
        assertEquals(26, merged.nextSlot());
        assertEquals(-1, merged.backSlot());  // explicitly hidden
        assertEquals(22, merged.closeSlot());
    }
}
