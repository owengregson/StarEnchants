package engine.interact;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.model.Ability;
import engine.stores.ReboundStore;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.DoubleSupplier;
import java.util.function.ToIntFunction;
import org.junit.jupiter.api.Test;
import testfx.Abilities;

/**
 * The rebound arbiter's claim decision (PROC_REBOUND). A claim is a VETO at gate 9, so a wrong answer here is
 * either an enchant that silently stops working on its owner or one that is never turned around — both silent.
 */
class ReboundPlanTest {

    private static final int NORMAL = 1;
    private static final int HEROIC = 2;
    private static final int MASTERY = 3;

    private final ReboundStore store = new ReboundStore();
    private final UUID reflector = UUID.randomUUID();

    /** An incoming ability at {@code level} whose source sits at rarity weight {@code tier}. */
    private ReboundPlan planFor(int tier, DoubleSupplier roll) {
        ToIntFunction<Ability> tiers = ability -> tier;
        return new ReboundPlan(store, reflector, tiers, roll);
    }

    private static Ability incoming(int id, int level) {
        return Abilities.ability().id(id).level(level).build();
    }

    /** Always claims when the gates pass — the roll is far below any authored chance. */
    private static DoubleSupplier alwaysRolls() {
        return () -> 0.0;
    }

    @Test
    void claimsAnEnchantInsideTheBandAtOrBelowTheReboundLevel() {
        store.arm(reflector, NORMAL, 4, 5.0, 0, 5);
        ReboundPlan plan = planFor(3, alwaysRolls());

        assertTrue(plan.claim(incoming(7, 4)));
        assertTrue(plan.claimedAny());
        assertArrayEquals(new int[] {7}, plan.claimed());
    }

    @Test
    void refusesAnEnchantAboveTheBand() {
        store.arm(reflector, NORMAL, 10, 100.0, 0, 5);
        assertFalse(planFor(6, alwaysRolls()).claim(incoming(7, 1)));
    }

    @Test
    void refusesAnEnchantBelowTheBand() {
        store.arm(reflector, MASTERY, 10, 100.0, 8, 8);
        assertFalse(planFor(7, alwaysRolls()).claim(incoming(7, 1)));
    }

    @Test
    void refusesASourceWithNoRarityTier() {
        // Pets, reforges and masks resolve to −1; the matrix's chain is over enchant tiers only.
        store.arm(reflector, NORMAL, 10, 100.0, 0, 5);
        assertFalse(planFor(-1, alwaysRolls()).claim(incoming(7, 1)));
    }

    @Test
    void refusesAnEnchantAboveTheReboundLevel() {
        store.arm(reflector, NORMAL, 4, 100.0, 0, 5);
        assertFalse(planFor(3, alwaysRolls()).claim(incoming(7, 5)),
                "rebound level must be at least the incoming enchant's level");
        assertTrue(planFor(3, alwaysRolls()).claim(incoming(7, 4)), "equal levels pass the gate");
    }

    @Test
    void theRollIsAgainstTheArmedGradesChanceAndIsDrawnOnlyOnceTheOtherGatesPass() {
        store.arm(reflector, NORMAL, 4, 5.0, 0, 5);
        AtomicInteger draws = new AtomicInteger();
        DoubleSupplier counted = () -> {
            draws.incrementAndGet();
            return 5.0; // exactly the chance — a strict < must NOT claim
        };

        ReboundPlan plan = planFor(3, counted);
        assertFalse(plan.claim(incoming(7, 4)));
        assertEquals(1, draws.get());

        ReboundPlan outOfBand = planFor(6, counted);
        assertFalse(outOfBand.claim(incoming(7, 4)));
        assertEquals(1, draws.get(), "an enchant no grade answers for must not consume a draw");
    }

    @Test
    void thePrecedenceChainPicksExactlyOneBranchPerIncomingTier() {
        // A wearer carrying all three grades: each incoming tier is answered by one, and its level gate is
        // the one that branch authored — the whole point of the exclusive chain.
        store.arm(reflector, NORMAL, 4, 100.0, 0, 5);
        store.arm(reflector, HEROIC, 6, 100.0, 6, 7);
        store.arm(reflector, MASTERY, 2, 100.0, 8, 8);

        assertTrue(planFor(5, alwaysRolls()).claim(incoming(7, 4)));
        assertTrue(planFor(7, alwaysRolls()).claim(incoming(7, 6)));
        assertFalse(planFor(7, alwaysRolls()).claim(incoming(7, 7)),
                "tier 7 is heroic's branch, so heroic's level 6 is the gate — not normal's or mastery's");
        assertFalse(planFor(8, alwaysRolls()).claim(incoming(7, 4)),
                "tier 8 is mastery's branch, whose level 2 the incoming level 4 exceeds");
    }

    @Test
    void repeatedClaimsOfOneAbilityAreRecordedSeparately() {
        // An ECHO_STRIKE second pass is a SECOND activation and is rolled — and rebounded — on its own.
        store.arm(reflector, NORMAL, 4, 100.0, 0, 5);
        ReboundPlan plan = planFor(3, alwaysRolls());

        assertTrue(plan.claim(incoming(7, 4)));
        assertTrue(plan.claim(incoming(7, 4)));

        assertArrayEquals(new int[] {7, 7}, plan.claimed());
    }

    @Test
    void anEmptyPlanClaimsNothing() {
        ReboundPlan plan = planFor(3, alwaysRolls());
        assertFalse(plan.claim(incoming(7, 1)), "nothing armed → nothing claimed");
        assertFalse(plan.claimedAny());
        assertArrayEquals(new int[0], plan.claimed());
    }

    @Test
    void theClaimListGrowsPastItsInitialCapacity() {
        store.arm(reflector, NORMAL, 4, 100.0, 0, 5);
        ReboundPlan plan = planFor(3, alwaysRolls());
        for (int id = 0; id < 9; id++) {
            assertTrue(plan.claim(incoming(id, 1)));
        }
        assertArrayEquals(new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8}, plan.claimed());
    }
}
