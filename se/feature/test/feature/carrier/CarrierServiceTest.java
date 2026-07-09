package feature.carrier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
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
    void salvageRefundIsUniformInOneToTierCostFlooredAtOne() {
        // The Tinkerer refund is a uniform draw in [1, N] where N is the tier's buy price — capped at what the
        // Enchanter charged so the buy→open→salvage loop can never profit. Pin the range, not the RNG sequence.
        Random rng = new Random(42);
        for (int cap : new int[] {0, 1, 5, 30}) {
            int hi = Math.max(1, cap);
            int seenLow = Integer.MAX_VALUE;
            int seenHigh = Integer.MIN_VALUE;
            for (int i = 0; i < 1000; i++) {
                int refund = CarrierService.salvageLevels(rng, cap);
                assertTrue(refund >= 1 && refund <= hi,
                        "refund " + refund + " out of [1," + hi + "] at cap " + cap);
                seenLow = Math.min(seenLow, refund);
                seenHigh = Math.max(seenHigh, refund);
            }
            if (cap <= 1) { // a 0-cost/unknown tier always refunds exactly one — worst case break-even
                assertEquals(1, seenLow);
                assertEquals(1, seenHigh);
            }
            if (cap == 5) { // both endpoints reachable → the range really is [1, N], not a constant
                assertEquals(1, seenLow, "the low endpoint 1 must occur");
                assertEquals(5, seenHigh, "the tier-cost endpoint must occur");
            }
        }
    }

    @Test
    void bookDisplayNameAppliesTheTemplateStylingIncludingUnderline() {
        // The EE-pack book name template (bold + underline) — the single renderer the menu icons reuse.
        String template = "{TIER_COLOR}&l&n{ENCHANT} {LEVEL}";
        // Level-less (apply/browse): the " {LEVEL}" slot is dropped, no trailing space.
        assertEquals("&e&l&nMolten", CarrierService.bookDisplayName(template, "&e", "Molten", ""));
        // Levelled (admin level view): the level fills the slot.
        assertEquals("&e&l&nMolten III", CarrierService.bookDisplayName(template, "&e", "Molten", "III"));
    }

    @Test
    void bookDisplayNameToleratesBothPlaceholderSpellingsAndNullColour() {
        assertEquals("&6Sharpness", CarrierService.bookDisplayName("{TIER-COLOR}{ENCHANT} {LEVEL}", "&6", "Sharpness", ""));
        assertEquals("Sharpness", CarrierService.bookDisplayName("{TIER_COLOR}{ENCHANT} {LEVEL}", null, "Sharpness", null));
    }
}
