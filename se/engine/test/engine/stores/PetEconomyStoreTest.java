package engine.stores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The two pet-family economy stores' contracts: the soul-cost waiver's window + thresholded feedback, and the
 * book-rate charge's one-shot, per-site, spent-by-the-attempt behaviour.
 */
class PetEconomyStoreTest {

    private static final UUID HOLDER = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();

    @Test
    void theWaiverIsHalfOpenAndScopedToItsHolder() {
        SoulExemptStore store = new SoulExemptStore();
        store.arm(HOLDER, 100L, 40, 0, "");

        assertTrue(store.waives(HOLDER, 100L), "the arming tick is inside the window");
        assertTrue(store.waives(HOLDER, 139L));
        assertFalse(store.waives(HOLDER, 140L), "half-open: the expiry tick is already free");
        assertFalse(store.waives(OTHER, 100L), "somebody else's exemption is not yours");
    }

    @Test
    void theEmptyStoreAnswersWithoutTouchingAKey() {
        // The gate-10 fast path: on a server where nobody is exempt the consult must not even hash a UUID.
        assertFalse(new SoulExemptStore().waives(HOLDER, 0L));
        assertNull(new SoulExemptStore().window(HOLDER, 0L));
    }

    @Test
    void theRefundLineIsSentOnlyAboveTheThreshold() {
        SoulExemptStore store = new SoulExemptStore();
        store.arm(HOLDER, 0L, 100, 10, "refund {souls}");

        assertEquals("", store.refundMessage(HOLDER, 0L, 10), "at the threshold is not ABOVE it");
        assertEquals("refund {souls}", store.refundMessage(HOLDER, 0L, 11));
        assertEquals("", store.refundMessage(HOLDER, 100L, 999), "an elapsed window says nothing at all");
    }

    @Test
    void aSilentWaiverStaysSilentHoweverLargeTheAmount() {
        SoulExemptStore store = new SoulExemptStore();
        store.arm(HOLDER, 0L, 100, 0, "");
        assertTrue(store.waives(HOLDER, 0L), "it still waives — silence is about feedback, not the waiver");
        assertEquals("", store.refundMessage(HOLDER, 0L, 5000));
    }

    @Test
    void aReArmReplacesTheWindowRatherThanExtendingIt() {
        SoulExemptStore store = new SoulExemptStore();
        store.arm(HOLDER, 0L, 100, 0, "first");
        store.arm(HOLDER, 10L, 20, 0, "second"); // expires at 30, NOT at 100+10

        assertEquals("second", store.refundMessage(HOLDER, 20L, 1), "the later arm's wording wins");
        assertFalse(store.waives(HOLDER, 30L), "and its expiry wins too — 30, not the first window's 100");
    }

    @Test
    void aBookChargeIsSpentByTheFirstReadWhateverTheRollReturns() {
        // "Consumed on the next roll regardless of outcome": consume() is called at the roll, before its
        // result exists, so there is no branch in which a failure could hand the charge back.
        BookRateStore store = new BookRateStore();
        store.arm(HOLDER, BookRateStore.APPLY, 7);

        assertEquals(7, store.armed(HOLDER, BookRateStore.APPLY), "armed() peeks without spending");
        assertEquals(7, store.consume(HOLDER, BookRateStore.APPLY));
        assertEquals(0, store.consume(HOLDER, BookRateStore.APPLY), "one shot, not a standing bonus");
        assertEquals(0, store.armed(HOLDER, BookRateStore.APPLY), "the paired guard fact reads clear again");
    }

    @Test
    void theTwoSitesAreIndependentCharges() {
        // The Blackscroll and Enchanter pets are both armable at once; spending one must not disarm the other.
        BookRateStore store = new BookRateStore();
        store.arm(HOLDER, BookRateStore.GENERATE, 3);
        store.arm(HOLDER, BookRateStore.APPLY, 9);

        assertEquals(3, store.consume(HOLDER, BookRateStore.GENERATE));
        assertEquals(9, store.armed(HOLDER, BookRateStore.APPLY), "the other site is untouched");
        assertEquals(9, store.consume(HOLDER, BookRateStore.APPLY));
    }

    @Test
    void aChargeBelongsToOnePlayerAndSurvivesAQuitSweep() {
        BookRateStore store = new BookRateStore();
        store.arm(HOLDER, BookRateStore.GENERATE, 5);

        assertEquals(0, store.armed(OTHER, BookRateStore.GENERATE));
        store.evictElapsed(HOLDER, Long.MAX_VALUE); // the quit sweep: a charge has no clock to elapse
        assertEquals(5, store.consume(HOLDER, BookRateStore.GENERATE),
                "a relog under a 15-minute pet cooldown must not eat the charge");
    }
}
