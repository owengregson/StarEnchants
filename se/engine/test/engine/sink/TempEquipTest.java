package engine.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The temporary equipment-swap ledger: record-once (original + placeholder), take-once, double-swap reject. */
class TempEquipTest {

    private final UUID p = UUID.randomUUID();

    @AfterEach
    void clean() {
        TempEquip.clearAll();
    }

    @Test
    void swapRecordsOriginalAndPlaceholderAndEndReturnsThemOnce() {
        ItemStack helmet = mock(ItemStack.class);
        assertTrue(TempEquip.swap(p, 3, helmet, Material.CARVED_PUMPKIN));
        assertTrue(TempEquip.isSwapped(p, 3));
        TempEquip.Swap swap = TempEquip.end(p, 3);
        assertEquals(helmet, swap.original());                 // same instance back
        assertEquals(Material.CARVED_PUMPKIN, swap.placeholder()); // the minted material is remembered
        assertFalse(TempEquip.isSwapped(p, 3));                // ended
        assertNull(TempEquip.end(p, 3));                       // nothing left
    }

    @Test
    void aSecondSwapOfTheSameSlotIsRejected() {
        TempEquip.swap(p, 3, mock(ItemStack.class), Material.CARVED_PUMPKIN);
        // never overwrite the captured original with the placeholder
        assertFalse(TempEquip.swap(p, 3, mock(ItemStack.class), Material.CARVED_PUMPKIN));
    }

    @Test
    void anEmptySlotRoundTripsAsAir() {
        assertTrue(TempEquip.swap(p, 3, null, Material.CARVED_PUMPKIN));
        assertTrue(TempEquip.isAir(TempEquip.end(p, 3).original())); // the slot was empty → restore to nothing
    }
}
