package engine.interact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SlotLedgerTest {

    @Test
    void maxIsBasePlusAddedAndRemainingSubtractsUsed() {
        SlotLedger s = new SlotLedger(10, 3, 4);
        assertEquals(13, s.max());
        assertEquals(9, s.remaining());
    }

    @Test
    void overfilledItemReportsZeroRemainingNotNegative() {
        SlotLedger s = new SlotLedger(10, 0, 12);
        assertEquals(0, s.remaining());
    }

    @Test
    void canApplyChecksRemaining() {
        SlotLedger s = new SlotLedger(10, 0, 8);
        assertTrue(s.canApply(2));
        assertFalse(s.canApply(3));
    }

    @Test
    void withAppliedAddsToUsedWithoutChangingCapacity() {
        SlotLedger s = new SlotLedger(10, 2, 4).withApplied(3);
        assertEquals(12, s.max());
        assertEquals(7, s.used());
        assertEquals(5, s.remaining());
    }

    @Test
    void withAddedSlotsGrowsCapacity() {
        SlotLedger s = new SlotLedger(10, 0, 4).withAddedSlots(5);
        assertEquals(15, s.max());
        assertEquals(11, s.remaining());
    }

    @Test
    void negativeCountsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new SlotLedger(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new SlotLedger(10, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new SlotLedger(10, 0, -1));
    }
}
