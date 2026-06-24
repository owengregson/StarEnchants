package feature.carrier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Pure tests for the carrier service's static book helpers (ADR-0016) — the combine-eligibility and
 * salvage-refund math the Alchemist/Tinkerer menus rely on. The mint + apply mutation (touching
 * {@code ItemStack}: book apply / destroy-on-fail / white-scroll guard / dust min–max + fixed) is verified
 * live in {@code CarrierSuite}; these need no server.
 */
class CarrierServiceTest {

    @Test
    void booksCombineOnlyWhenSameEnchantSameLevelBelowMax() {
        assertTrue(CarrierService.combinable("enchants/zap", 1, "enchants/zap", 1, 3), "same key+level below max");
        assertFalse(CarrierService.combinable("enchants/zap", 1, "enchants/frost", 1, 3), "different enchants");
        assertFalse(CarrierService.combinable("enchants/zap", 1, "enchants/zap", 2, 3), "different levels");
        assertFalse(CarrierService.combinable("enchants/zap", 3, "enchants/zap", 3, 3), "already at max");
        assertFalse(CarrierService.combinable("enchants/zap", 0, "enchants/zap", 0, 3), "level below one");
    }

    @Test
    void salvageRefundsTheBookLevelAtLeastOne() {
        assertEquals(1, CarrierService.salvageLevels(1));
        assertEquals(5, CarrierService.salvageLevels(5));
        assertEquals(1, CarrierService.salvageLevels(0), "a zero/garbage level still refunds at least one");
    }
}
