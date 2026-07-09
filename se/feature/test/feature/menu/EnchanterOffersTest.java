package feature.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.load.TierRegistry;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for {@link EnchanterOffers} — the default Enchanter shop pricing/ordering, server-free.
 * (The tier registry itself is exercised live; here the cost curve is pinned so a pricing regression fails
 * in CI rather than silently shipping free/overpriced books.)
 */
class EnchanterOffersTest {

    @Test
    void bookCostUsesExplicitTierCostWhenSetElseDerivesFromWeight() {
        // An explicit cost: (>= 0) overrides the weight formula — the configurable per-tier book price.
        assertEquals(7, new TierRegistry.Tier("rare", "&b", 30, false, 7).bookCostLevels());
        // 0 is a valid explicit "free" tier (>= 0), distinct from the derive sentinel.
        assertEquals(0, new TierRegistry.Tier("freebie", "&7", 10, false, 0).bookCostLevels());
        // -1 (the omitted-cost sentinel) derives max(1, weight/5): floored at one, scaling with weight.
        assertEquals(1, new TierRegistry.Tier("dust", "&7", 0, false, -1).bookCostLevels()); // never free
        assertEquals(1, new TierRegistry.Tier("dust", "&7", 5, false, -1).bookCostLevels());
        assertEquals(2, new TierRegistry.Tier("common", "&7", 10, false, -1).bookCostLevels());
        assertEquals(12, new TierRegistry.Tier("mythic", "&c", 60, false, -1).bookCostLevels());
    }

    @Test
    void priceForDelegatesToBookCostLevels() {
        // The Enchanter charge and the Tinkerer salvage cap read the SAME rule — priceFor is a thin delegate.
        TierRegistry.Tier explicit = new TierRegistry.Tier("rare", "&b", 30, false, 7);
        assertEquals(explicit.bookCostLevels(), EnchanterOffers.priceFor(explicit));
        TierRegistry.Tier derived = new TierRegistry.Tier("rare", "&b", 30, false, -1);
        assertEquals(derived.bookCostLevels(), EnchanterOffers.priceFor(derived));
        assertTrue(EnchanterOffers.priceFor(derived) > 0, "a derived price is always at least one level");
    }
}
