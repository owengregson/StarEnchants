package engine.sink;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** {@code INVENTORY_CONVERT}'s budget arithmetic, hand-computed — above all the cap-straddling direction. */
class ConversionPlanTest {

    @Test
    void aStackStraddlingTheLimitConvertsUpToItAndReturnsTheOverflow() {
        // Budget 10, one stack of 16. INTENDED: convert 10, hand back 6.
        // The recorded jar bug (D-12-4/D-12-6) is the exact inverse — it converted the 6 that did NOT fit and
        // handed back the 10 that did, so the fuller the budget the less it converted.
        int[] plan = ConversionPlan.plan(new int[] {16}, 10);

        assertEquals(10, ConversionPlan.converted(plan));
        assertArrayEquals(new int[] {10, 0, 10, 6}, plan);
    }

    @Test
    void theBudgetIsSpentInSlotOrderAndStopsDeadWhenExhausted() {
        // Slots of 16/16/16 under a budget of 20: the first slot goes whole, the second straddles, the third
        // is never reached at all (a plan that listed it would clear a stack it never converted).
        int[] plan = ConversionPlan.plan(new int[] {16, 16, 16}, 20);

        assertEquals(20, ConversionPlan.converted(plan));
        assertArrayEquals(new int[] {20, 0, 16, 0, 1, 4, 12}, plan);
    }

    @Test
    void ineligibleSlotsAreSkippedWithoutSpendingBudget() {
        // Slot 1 is somebody's named bucket (or a different material): it contributes nothing AND costs
        // nothing, so the budget flows past it to slot 2.
        int[] plan = ConversionPlan.plan(new int[] {4, ConversionPlan.SKIP, 5}, 100);

        assertEquals(9, ConversionPlan.converted(plan));
        assertArrayEquals(new int[] {9, 0, 4, 0, 2, 5, 0}, plan);
    }

    @Test
    void aBudgetLargerThanTheInventoryConvertsEverythingEligible() {
        // The L10 Lava pet: limit 1152 over a handful of stacks. Nothing is handed back, and the count the
        // activation prices itself on is the real total, not the limit.
        int[] plan = ConversionPlan.plan(new int[] {16, 16, 3}, 1152);
        assertEquals(35, ConversionPlan.converted(plan));
        assertArrayEquals(new int[] {35, 0, 16, 0, 1, 16, 0, 2, 3, 0}, plan);
    }

    @Test
    void nothingEligibleIsAZeroPlanNotAnEmptyOne() {
        // The zero-converted failure branch reads this count, so it has to be a real 0 rather than absent.
        assertEquals(0, ConversionPlan.converted(ConversionPlan.plan(new int[] {0, 0}, 64)));
        assertEquals(0, ConversionPlan.converted(ConversionPlan.plan(new int[] {16}, 0)));
    }
}
