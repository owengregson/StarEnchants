package engine.stores;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class SuppressionStoreTest {

    private final SuppressionStore store = new SuppressionStore();
    private final UUID p = UUID.randomUUID();

    @Test
    void unsuppressedIdIsNotSuppressed() {
        assertFalse(store.isSuppressed(p, 1, 0L));
    }

    @Test
    void suppressedIdIsSuppressedUntilExpiry() {
        store.suppress(p, 1, 100L, 40);
        assertTrue(store.isSuppressed(p, 1, 100L));
        assertTrue(store.isSuppressed(p, 1, 139L));
        assertFalse(store.isSuppressed(p, 1, 140L));
    }

    @Test
    void idsAreIndependent() {
        store.suppress(p, 7, 0L, 100);
        assertTrue(store.isSuppressed(p, 7, 0L));
        assertFalse(store.isSuppressed(p, 8, 0L));
    }

    @Test
    void playersAreIndependent() {
        UUID q = UUID.randomUUID();
        store.suppress(p, 1, 0L, 100);
        assertTrue(store.isSuppressed(p, 1, 0L));
        assertFalse(store.isSuppressed(q, 1, 0L));
    }

    @Test
    void zeroOrNegativeDurationSuppressesNothing() {
        store.suppress(p, 1, 0L, 0);
        store.suppress(p, 1, 0L, -5);
        assertFalse(store.isSuppressed(p, 1, 0L));
    }

    @Test
    void reSuppressingExtendsButNeverShortens() {
        store.suppress(p, 1, 0L, 100);
        store.suppress(p, 1, 10L, 20); // a shorter re-suppress (would expire at 30) must NOT shorten the live window
        assertTrue(store.isSuppressed(p, 1, 50L));
        assertFalse(store.isSuppressed(p, 1, 100L)); // the original expiry still governs

        store.suppress(p, 1, 0L, 100);
        store.suppress(p, 1, 0L, 200); // a longer re-suppress extends the window
        assertTrue(store.isSuppressed(p, 1, 150L));
        assertFalse(store.isSuppressed(p, 1, 200L));
    }

    @Test
    void elapsedSuppressionIsEvictedLazily() {
        store.suppress(p, 1, 0L, 10);
        assertFalse(store.isSuppressed(p, 1, 10L)); // elapsed read evicts the entry
        assertFalse(store.isSuppressed(p, 1, 100L));
        store.suppress(p, 1, 100L, 5); // a fresh suppress after eviction still arms cleanly
        assertTrue(store.isSuppressed(p, 1, 100L));
    }

    @Test
    void immunePlayerCannotBeSuppressedAndArmingClearsExistingSuppression() {
        store.suppress(p, 1, 0L, 100);
        assertTrue(store.isSuppressed(p, 1, 0L)); // suppressed before immunity
        store.setImmune(p, 100);
        assertTrue(store.isImmune(p));
        assertFalse(store.isSuppressed(p, 1, 0L)); // arming immunity dropped the existing suppression
        store.suppress(p, 1, 0L, 100);             // and a fresh suppress is vetoed at the write
        assertFalse(store.isSuppressed(p, 1, 0L));
    }

    @Test
    void partialImmunityRollsPerSuppressionAndIsNotAbsolute() {
        store.setImmune(p, 50); // 50% chance to ignore each suppression (ADR-0032 crystal "ignore Silence")
        assertFalse(store.isImmune(p), "a partial chance is not ABSOLUTE immunity");
        int landed = 0;
        for (int id = 0; id < 600; id++) {         // distinct ids so each is an independent roll, not an extend
            store.suppress(p, id, 0L, 100);
            if (store.isSuppressed(p, id, 0L)) {
                landed++;
            }
        }
        // The roll actually varies — a "never vetoes" (0) or "always vetoes" (600) bug is caught. ~300 expected;
        // the wide band never flakes.
        int total = landed;
        assertTrue(total > 120 && total < 480, () -> "landed=" + total);
    }

    @Test
    void partialImmunityDoesNotClearExistingSuppression() {
        store.suppress(p, 1, 0L, 100);
        store.setImmune(p, 50);                    // only ABSOLUTE (>=100) immunity drops a live suppression
        assertTrue(store.isSuppressed(p, 1, 0L));
    }

    @Test
    void liftingImmunityRestoresSuppressibility() {
        store.setImmune(p, 100);
        store.suppress(p, 1, 0L, 100);
        assertFalse(store.isSuppressed(p, 1, 0L)); // vetoed while immune
        store.setImmune(p, 0);
        assertFalse(store.isImmune(p));
        store.suppress(p, 1, 0L, 100);
        assertTrue(store.isSuppressed(p, 1, 0L));   // suppressible again
    }

    @Test
    void clearAndClearAllLiftImmunity() {
        store.setImmune(p, 100);
        store.clear(p);
        assertFalse(store.isImmune(p));
        store.setImmune(p, 100);
        store.clearAll();
        assertFalse(store.isImmune(p));
    }

    @Test
    void clearForgetsOnePlayerAndClearAllForgetsEveryone() {
        UUID q = UUID.randomUUID();
        store.suppress(p, 1, 0L, 100);
        store.suppress(q, 1, 0L, 100);
        store.clear(p);
        assertFalse(store.isSuppressed(p, 1, 0L));
        assertTrue(store.isSuppressed(q, 1, 0L));
        store.clearAll();
        assertFalse(store.isSuppressed(q, 1, 0L));
    }
}
