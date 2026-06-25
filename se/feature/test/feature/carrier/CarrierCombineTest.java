package feature.carrier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for the Alchemist/Tinkerer economy decisions on {@link CarrierService} (server-free). The
 * live suite proves the item read + mint + EXP grant; these pin the combine guard (same enchant, same level,
 * below max) and the salvage refund curve so a balance/loss regression fails in CI.
 */
class CarrierCombineTest {

    @Test
    void combinableRequiresSameEnchantAndLevelBelowMax() {
        assertTrue(CarrierService.combinable("enchants/keen", 1, "enchants/keen", 1, 5));
        assertTrue(CarrierService.combinable("enchants/keen", 4, "enchants/keen", 4, 5));
    }

    @Test
    void combinableRejectsDifferentEnchantOrLevel() {
        assertFalse(CarrierService.combinable("enchants/keen", 1, "enchants/venom", 1, 5));
        assertFalse(CarrierService.combinable("enchants/keen", 1, "enchants/keen", 2, 5));
    }

    @Test
    void combinableRejectsAtOrAboveMaxLevel() {
        assertFalse(CarrierService.combinable("enchants/keen", 5, "enchants/keen", 5, 5)); // already max
        assertFalse(CarrierService.combinable("enchants/keen", 0, "enchants/keen", 0, 5)); // level 0 is not a book
    }

    @Test
    void salvageLevelsRefundsTheBookLevelAtLeastOne() {
        assertEquals(1, CarrierService.salvageLevels(0)); // never a zero refund
        assertEquals(1, CarrierService.salvageLevels(1));
        assertEquals(5, CarrierService.salvageLevels(5));
    }
}
