package feature.combat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class RepairGuardMathTest {

    @Test
    void thresholdsAreFifteenTwentyAndTwentyFivePercentRemaining() {
        assertFalse(RepairGuardService.belowThreshold(itemWithRemainingPoints(13), 1));
        assertTrue(RepairGuardService.belowThreshold(itemWithRemainingPoints(12), 1));
        assertFalse(RepairGuardService.belowThreshold(itemWithRemainingPoints(17), 2));
        assertTrue(RepairGuardService.belowThreshold(itemWithRemainingPoints(16), 2));
        assertFalse(RepairGuardService.belowThreshold(itemWithRemainingPoints(21), 3));
        assertTrue(RepairGuardService.belowThreshold(itemWithRemainingPoints(20), 3));
    }

    @Test
    void nonDamageableItemsCannotProc() {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(Material.STONE);
        assertFalse(RepairGuardService.belowThreshold(item, 3));
    }

    @SuppressWarnings("deprecation")
    private static ItemStack itemWithRemainingPoints(int remaining) {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(Material.LEATHER_CHESTPLATE);
        int max = Material.LEATHER_CHESTPLATE.getMaxDurability();
        when(item.getDurability()).thenReturn((short) (max - remaining));
        return item;
    }
}
