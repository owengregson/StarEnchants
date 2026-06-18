package feature.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for {@link EnchanterOffers} — the default Enchanter shop pricing/ordering, server-free.
 * (The tier registry itself is exercised live; here the cost curve is pinned so a pricing regression fails
 * in CI rather than silently shipping free/overpriced books.)
 */
class EnchanterOffersTest {

    @Test
    void costScalesWithTierWeightButIsAlwaysAtLeastOne() {
        assertEquals(1, EnchanterOffers.cost(0));   // a zero-weight tier still costs a level (never free)
        assertEquals(1, EnchanterOffers.cost(5));
        assertEquals(2, EnchanterOffers.cost(10));  // common (weight 10) → 2 levels
        assertEquals(12, EnchanterOffers.cost(60)); // mythic (weight 60) → 12 levels
        assertTrue(EnchanterOffers.cost(60) > EnchanterOffers.cost(10), "rarer tiers cost more");
    }
}
