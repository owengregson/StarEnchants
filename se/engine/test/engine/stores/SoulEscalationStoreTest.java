package engine.stores;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The escalating soul price: the cost formula, and the per-(player, ability) counter that feeds it. */
class SoulEscalationStoreTest {

    private static final UUID PLAYER = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();
    private static final long SCOPE = CooldownStore.key(0, 11);
    private static final long OTHER_SCOPE = CooldownStore.key(0, 12);

    private final SoulEscalationStore store = new SoulEscalationStore();

    @Test
    void thePriceLadderCompoundsPerStepAndClampsAtTheCap() {
        // The shipped consumer (Phoenix): base 500, growth 2, cap 8000 — 500/1000/2000/4000/8000, then flat.
        // An off-by-one in the exponent (growth^(steps+1)) or a missing clamp both fail here.
        assertEquals(List.of(500, 1000, 2000, 4000, 8000, 8000, 8000),
                ladder(500, 2.0, 8000, 7));
    }

    @Test
    void anUncappedLadderKeepsCompounding() {
        // cap 0 = uncapped: the 5th charge must NOT be silently held at the 4th price.
        assertEquals(List.of(500, 1000, 2000, 4000, 8000, 16000), ladder(500, 2.0, 0, 6));
    }

    @Test
    void growthOfOneIsTheStaticPriceForever() {
        // The default envelope: an authored ability with no escalation knobs pays exactly soul-cost, always.
        assertEquals(List.of(7, 7, 7, 7), ladder(7, 1.0, 0, 4));
    }

    @Test
    void aFractionalPriceRoundsRatherThanTruncates() {
        // 100 * 1.5^1 = 150; 100 * 1.5^3 = 337.5 → 338. Truncation would under-charge by a soul every odd step.
        assertEquals(List.of(100, 150, 225, 338), ladder(100, 1.5, 0, 4));
    }

    @Test
    void stepsAdvanceOncePerChargeAndAreScopedPerAbility() {
        // One counter per (player, ability): a second ability must not inherit the first's price.
        store.step(PLAYER, SCOPE, 0L, 0);
        store.step(PLAYER, SCOPE, 1L, 0);

        assertEquals(2, store.steps(PLAYER, SCOPE, 2L, 0));
        assertEquals(0, store.steps(PLAYER, OTHER_SCOPE, 2L, 0));
        assertEquals(0, store.steps(OTHER, SCOPE, 2L, 0));
    }

    @Test
    void aZeroDecayPeriodNeverDecays() {
        store.step(PLAYER, SCOPE, 0L, 0);
        assertEquals(1, store.steps(PLAYER, SCOPE, 1_000_000L, 0));
    }

    @Test
    void oneStepDecaysPerElapsedPeriodAndFloorsAtZero() {
        store.step(PLAYER, SCOPE, 0L, 100);
        store.step(PLAYER, SCOPE, 0L, 100);
        store.step(PLAYER, SCOPE, 0L, 100); // 3 charges at tick 0

        assertEquals(3, store.steps(PLAYER, SCOPE, 99L, 100), "a partial period must not decay");
        assertEquals(2, store.steps(PLAYER, SCOPE, 100L, 100));
        assertEquals(2, store.steps(PLAYER, SCOPE, 199L, 100));
        assertEquals(1, store.steps(PLAYER, SCOPE, 200L, 100));
        assertEquals(0, store.steps(PLAYER, SCOPE, 300L, 100), "the counter floors at 0 — the base price");
        assertEquals(0, store.steps(PLAYER, SCOPE, 30_000L, 100), "and stays there, never going negative");
    }

    @Test
    void aChargeAfterPartialDecayResumesFromTheDecayedCount() {
        // The trap: stepping from the STORED count instead of the decayed one skips a rung — a player who
        // waited out two periods would jump straight back to the old price on the next proc.
        store.step(PLAYER, SCOPE, 0L, 100);
        store.step(PLAYER, SCOPE, 0L, 100);
        store.step(PLAYER, SCOPE, 0L, 100); // 3 charges at tick 0

        store.step(PLAYER, SCOPE, 250L, 100); // 2 periods elapsed → 3 - 2 = 1, then +1

        assertEquals(2, store.steps(PLAYER, SCOPE, 250L, 100));
        assertEquals(2, store.steps(PLAYER, SCOPE, 349L, 100), "the decay clock restarts at the charge");
    }

    /** The first {@code charges} prices for an ability, walking the counter one successful charge at a time. */
    private List<Integer> ladder(int baseCost, double growth, int cap, int charges) {
        UUID player = UUID.randomUUID();
        return java.util.stream.IntStream.range(0, charges).mapToObj(i -> {
            int cost = SoulEscalationStore.escalatedCost(baseCost, growth, cap,
                    store.steps(player, SCOPE, i, 0));
            store.step(player, SCOPE, i, 0);
            return cost;
        }).toList();
    }
}
