package item.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Pure parse/serialize tests for the applied-utility marker set. The on-{@link org.bukkit.inventory.ItemStack}
 * occupy/release + the trak/scroll coexistence are exercised live; these pin the blob format with no server —
 * crucially the legacy single-value compat, since a wrong parse would silently un-apply existing items' markers.
 */
class AppliedSlotTest {

    @Test
    void emptyAndBlankParseToNoMarkers() {
        assertEquals(Set.of(), AppliedSlot.parse(null));
        assertEquals(Set.of(), AppliedSlot.parse(""));
        assertEquals(Set.of(), AppliedSlot.parse("   "));
    }

    @Test
    void legacySingleValueParsesToOneElement() {
        // The pre-set one-occupant model stored a bare kind; it must still read back as that one applied marker.
        assertEquals(Set.of(AppliedSlot.MOBTRAK), AppliedSlot.parse(AppliedSlot.MOBTRAK));
    }

    @Test
    void multipleMarkersParseAndPreserveInsertionOrder() {
        Set<String> out = AppliedSlot.parse(AppliedSlot.MOBTRAK + "," + AppliedSlot.SOULTRAK);
        assertEquals(List.of(AppliedSlot.MOBTRAK, AppliedSlot.SOULTRAK), List.copyOf(out));
    }

    @Test
    void blanksAndDuplicatesAreDropped() {
        assertEquals(Set.of(AppliedSlot.MOBTRAK),
                AppliedSlot.parse(AppliedSlot.MOBTRAK + ",," + AppliedSlot.MOBTRAK));
    }

    @Test
    void serializeJoinsWithCommaAndEmptyIsBlank() {
        Set<String> markers = new LinkedHashSet<>(List.of(AppliedSlot.MOBTRAK, AppliedSlot.SOULTRAK));
        assertEquals(AppliedSlot.MOBTRAK + "," + AppliedSlot.SOULTRAK, AppliedSlot.serialize(markers));
        assertTrue(AppliedSlot.serialize(Set.of()).isEmpty());
    }

    @Test
    void serializeThenParseRoundTripsAFullSet() {
        Set<String> markers = new LinkedHashSet<>(
                List.of(AppliedSlot.WHITE_SCROLL, AppliedSlot.HOLY, AppliedSlot.BLOCKTRAK));
        assertEquals(markers, AppliedSlot.parse(AppliedSlot.serialize(markers)));
    }
}
