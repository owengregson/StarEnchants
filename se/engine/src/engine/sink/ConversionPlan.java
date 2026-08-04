package engine.sink;

import java.util.Arrays;

/**
 * {@code INVENTORY_CONVERT}'s budget arithmetic, kept pure so the cap-straddling rule is hand-checkable
 * without an inventory: given each slot's eligible amount and a conversion limit, how much of each slot
 * converts and how much is handed back.
 *
 * <p>The rule the recorded original got backwards: a stack that straddles the remaining limit converts up to
 * the limit and returns the OVERFLOW. The jar converted the overflow and returned the part that fitted, so a
 * budget with 2 left over a stack of 16 converted 14 instead of 2.
 */
public final class ConversionPlan {

    private ConversionPlan() {
    }

    /** {@code amounts[i] <= 0} marks slot {@code i} ineligible (wrong material, or meta under {@code plain}). */
    public static final int SKIP = 0;

    /**
     * The plan as {@code [total, slot, take, leftover, slot, take, leftover, …]} — one triple per slot that
     * contributes, in slot order, stopping as soon as the limit is exhausted.
     */
    public static int[] plan(int[] amounts, int limit) {
        int[] plan = new int[1 + amounts.length * 3];
        int next = 1;
        int remaining = Math.max(0, limit);
        for (int slot = 0; slot < amounts.length && remaining > 0; slot++) {
            int amount = amounts[slot];
            if (amount <= SKIP) {
                continue;
            }
            int take = Math.min(amount, remaining);
            plan[next++] = slot;
            plan[next++] = take;
            plan[next++] = amount - take; // the overflow, handed back as the SOURCE material
            remaining -= take;
        }
        plan[0] = Math.max(0, limit) - remaining;
        return Arrays.copyOf(plan, next);
    }

    /** How many items {@code plan} converts in total. */
    public static int converted(int[] plan) {
        return plan[0];
    }
}
