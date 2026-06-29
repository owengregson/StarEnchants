package item.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pure tests for the applied-scroll PROTECTED lines: the (guarded, holy) marker state maps to the ordered,
 * colour-translated lines. The templates are test-owned inputs (not the shipped strings), so the assertions
 * pin the mapping/order without re-typing the catalogue; the on-item render is covered live.
 */
class ProtectionLoreTest {

    @Test
    void noMarkersYieldNoLines() {
        assertEquals(List.of(), ProtectionLore.lines(false, false, "&aWHITE", "&bHOLY"));
    }

    @Test
    void onlyTheWhiteGuardLineForAGuardedItem() {
        assertEquals(List.of(Colors.translate("&aWHITE")),
                ProtectionLore.lines(true, false, "&aWHITE", "&bHOLY"));
    }

    @Test
    void onlyTheHolyLineForAHolyItem() {
        assertEquals(List.of(Colors.translate("&bHOLY")),
                ProtectionLore.lines(false, true, "&aWHITE", "&bHOLY"));
    }

    @Test
    void bothLinesWhiteThenHolyWhenBothMarkersPresent() {
        assertEquals(List.of(Colors.translate("&aWHITE"), Colors.translate("&bHOLY")),
                ProtectionLore.lines(true, true, "&aWHITE", "&bHOLY"));
    }
}
