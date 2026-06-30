package engine.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.UUID;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The temporary equipment-swap ledger: record-once, take-once, double-swap reject, empty-slot → air sentinel. */
class TempEquipTest {

    private final UUID p = UUID.randomUUID();

    @AfterEach
    void clean() {
        TempEquip.clearAll();
    }

    @Test
    void swapRecordsAndEndReturnsTheOriginalOnce() {
        ItemStack helmet = mock(ItemStack.class);
        assertTrue(TempEquip.swap(p, 3, helmet));
        assertTrue(TempEquip.isSwapped(p, 3));
        assertEquals(helmet, TempEquip.end(p, 3)); // same instance back
        assertFalse(TempEquip.isSwapped(p, 3));     // ended
        assertNull(TempEquip.end(p, 3));            // nothing left
    }

    @Test
    void aSecondSwapOfTheSameSlotIsRejected() {
        TempEquip.swap(p, 3, mock(ItemStack.class));
        assertFalse(TempEquip.swap(p, 3, mock(ItemStack.class))); // never overwrite the captured original
    }

    @Test
    void anEmptySlotRoundTripsAsAir() {
        assertTrue(TempEquip.swap(p, 3, null));
        assertTrue(TempEquip.isAir(TempEquip.end(p, 3))); // the slot was empty → restore to nothing
    }
}
