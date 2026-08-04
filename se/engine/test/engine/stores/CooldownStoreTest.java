package engine.stores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CooldownStoreTest {

    private final CooldownStore store = new CooldownStore();
    private final UUID p = UUID.randomUUID();

    @Test
    void unarmedScopeIsReady() {
        assertTrue(store.ready(p, 1L, 0L));
        assertEquals(0L, store.remainingTicks(p, 1L, 0L));
    }

    @Test
    void armedScopeIsNotReadyUntilExpiry() {
        store.arm(p, 1L, 100L, 40);
        assertFalse(store.ready(p, 1L, 100L));
        assertFalse(store.ready(p, 1L, 139L));
        assertEquals(1L, store.remainingTicks(p, 1L, 139L));
        assertTrue(store.ready(p, 1L, 140L)); // the expiry tick itself is ready (exclusive upper bound)
    }

    @Test
    void scopesAreIndependent() {
        store.arm(p, CooldownStore.key(1, 7), 0L, 100);
        assertFalse(store.ready(p, CooldownStore.key(1, 7), 0L));
        assertTrue(store.ready(p, CooldownStore.key(2, 7), 0L)); // different kind, same id
        assertTrue(store.ready(p, CooldownStore.key(1, 8), 0L)); // same kind, different id
    }

    @Test
    void zeroOrNegativeDurationArmsNothing() {
        store.arm(p, 1L, 0L, 0);
        store.arm(p, 1L, 0L, -5);
        assertTrue(store.ready(p, 1L, 0L));
    }

    @Test
    void keyPackingIsCollisionFreeAcrossKindAndId() {
        assertEquals(CooldownStore.key(1, 7), CooldownStore.key(1, 7));
        org.junit.jupiter.api.Assertions.assertNotEquals(CooldownStore.key(1, 7), CooldownStore.key(2, 7));
        org.junit.jupiter.api.Assertions.assertNotEquals(CooldownStore.key(1, 7), CooldownStore.key(1, 8));
    }

    @Test
    void targetBucketsCoolDownIndependently() {
        // The mob route (bucket 0) and the player route (bucket 1) are separate cooldowns for the same scope.
        store.arm(p, CooldownStore.key(1, 7, 0), 0L, 100);
        assertFalse(store.ready(p, CooldownStore.key(1, 7, 0), 0L)); // mob route armed
        assertTrue(store.ready(p, CooldownStore.key(1, 7, 1), 0L));  // player route still ready
        // Bucket 0 packs identically to the two-arg key, so non-bucketed call sites (suppression) are unaffected.
        assertEquals(CooldownStore.key(1, 7), CooldownStore.key(1, 7, 0));
        org.junit.jupiter.api.Assertions.assertNotEquals(CooldownStore.key(1, 7, 0), CooldownStore.key(1, 7, 1));
    }

    @Test
    void perVictimKeysAreADimensionOfTheirOwnNotExtraKeyBits() {
        // `cooldown-per-victim`: each victim gets its own window under the SAME packed scope key. A victim
        // hashed into the packed long instead would collide silently — a phantom cooldown on an unrelated mob.
        UUID victimA = UUID.randomUUID();
        UUID victimB = UUID.randomUUID();
        long scope = CooldownStore.key(0, 7, 0);
        store.arm(p, victimA, scope, 0L, 100);
        assertFalse(store.ready(p, victimA, scope, 0L));
        assertTrue(store.ready(p, victimB, scope, 0L)); // a second victim carries its own window
        assertTrue(store.ready(p, scope, 0L));          // and the coarse bucket is untouched
        store.arm(p, scope, 0L, 100);
        assertTrue(store.ready(p, victimB, scope, 0L), "the coarse bucket must not block a per-victim key");
    }

    @Test
    void perVictimReserveAndReleaseTouchOnlyThatVictimsWindow() {
        // The atomic reserve/release contract has to hold in the new dimension too: routing either half to the
        // coarse map would arm (or roll back) a window the gate never checked.
        UUID victimA = UUID.randomUUID();
        UUID victimB = UUID.randomUUID();
        long scope = CooldownStore.key(0, 7, 0);
        assertEquals(0L, store.tryAcquire(p, victimA, scope, 100L, 40)); // reserved to 140
        assertEquals(40L, store.tryAcquire(p, victimA, scope, 100L, 40)); // same victim: held
        assertEquals(0L, store.tryAcquire(p, victimB, scope, 100L, 40)); // other victim: free
        assertTrue(store.ready(p, scope, 100L), "no reservation leaked into the coarse bucket");
        store.release(p, victimA, scope, 140L);
        assertTrue(store.ready(p, victimA, scope, 100L));
        assertFalse(store.ready(p, victimB, scope, 100L), "a release must not clear a sibling victim");
    }

    @Test
    void evictElapsedReclaimsPerVictimEntriesSoAKillFarmCannotLeak() {
        // Without this the store retains one entry per mob a player ever hit — and mobs die and never return,
        // so nothing would ever read (and lazily evict) those keys again.
        UUID q = UUID.randomUUID();
        long scope = CooldownStore.key(0, 7, 0);
        UUID live = UUID.randomUUID();
        for (int i = 0; i < 8; i++) {
            store.arm(p, UUID.randomUUID(), scope, 0L, 40); // expire at 40
        }
        store.arm(p, live, scope, 0L, 200);
        store.arm(q, UUID.randomUUID(), scope, 0L, 40);
        assertEquals(9, store.trackedVictims(p));

        store.evictElapsed(p, 100L); // the quit sweep
        assertEquals(1, store.trackedVictims(p), "an emptied victim map is dropped, not left as an empty shell");
        assertFalse(store.ready(p, live, scope, 100L), "a live window survives the sweep");

        store.evictElapsed(100L); // the periodic all-players sweep
        assertEquals(0, store.trackedVictims(q));
        assertEquals(1, store.trackedVictims(p));
        store.evictElapsed(300L);
        assertEquals(0, store.trackedVictims(p), "once every window has elapsed the player is dropped entirely");
    }

    @Test
    void clearForgetsPerVictimWindowsToo() {
        UUID victim = UUID.randomUUID();
        store.arm(p, victim, 1L, 0L, 100);
        store.clear(p);
        assertTrue(store.ready(p, victim, 1L, 0L));
        store.arm(p, victim, 1L, 0L, 100);
        store.clearAll();
        assertTrue(store.ready(p, victim, 1L, 0L));
    }

    @Test
    void clearForgetsOnePlayerAndClearAllForgetsEveryone() {
        UUID q = UUID.randomUUID();
        store.arm(p, 1L, 0L, 100);
        store.arm(q, 1L, 0L, 100);
        store.clear(p);
        assertTrue(store.ready(p, 1L, 0L));
        assertFalse(store.ready(q, 1L, 0L));
        store.clearAll();
        assertTrue(store.ready(q, 1L, 0L));
    }

    @Test
    void tryAcquireInstallsTheReservationWhenReady() {
        assertEquals(0L, store.tryAcquire(p, 1L, 100L, 40)); // acquired
        assertFalse(store.ready(p, 1L, 100L));               // now armed to 140
        assertEquals(40L, store.remainingTicks(p, 1L, 100L));
    }

    @Test
    void tryAcquireReportsRemainingAndDoesNotOverwriteALiveReservation() {
        store.arm(p, 1L, 100L, 40); // live to 140
        assertEquals(20L, store.tryAcquire(p, 1L, 120L, 999)); // reports remaining, does not extend
        assertEquals(20L, store.remainingTicks(p, 1L, 120L));  // still the original expiry
    }

    @Test
    void tryAcquireReAcquiresAnElapsedReservation() {
        store.arm(p, 1L, 0L, 40);                         // expires at 40
        assertEquals(0L, store.tryAcquire(p, 1L, 40L, 10)); // elapsed → taken over
        assertEquals(10L, store.remainingTicks(p, 1L, 40L));
    }

    @Test
    void tryAcquireIsCheckOnlyForANonPositiveDuration() {
        // A zero-cooldown ability must still respect a shared scope another ability armed, but writes nothing itself.
        assertEquals(0L, store.tryAcquire(p, 1L, 0L, 0)); // ready, nothing written
        assertTrue(store.ready(p, 1L, 0L));
        store.arm(p, 1L, 0L, 100);
        assertEquals(100L, store.tryAcquire(p, 1L, 0L, 0)); // sees another ability's live reservation
        // A check-only acquire against an ELAPSED entry evicts it and reports ready.
        assertEquals(0L, store.tryAcquire(p, 1L, 100L, 0));
        assertTrue(store.ready(p, 1L, 100L));
    }

    @Test
    void releaseRemovesOnlyTheExactReservation() {
        store.tryAcquire(p, 1L, 100L, 40);   // reserved expiry = 140
        store.release(p, 1L, 999L);          // wrong expiry → no-op
        assertFalse(store.ready(p, 1L, 100L));
        store.release(p, 1L, 140L);          // exact expiry → rolled back
        assertTrue(store.ready(p, 1L, 100L));
    }

    @Test
    void evictElapsedDropsElapsedEntriesAndEmptiesThePerPlayerMap() {
        store.arm(p, CooldownStore.key(0, 1), 0L, 40);  // expires at 40
        store.arm(p, CooldownStore.key(0, 2), 0L, 200); // expires at 200
        store.evictElapsed(p, 100L);
        assertTrue(store.ready(p, CooldownStore.key(0, 1), 100L)); // elapsed one dropped
        assertFalse(store.ready(p, CooldownStore.key(0, 2), 100L)); // live one kept
        // once every entry has elapsed, the per-player map is dropped entirely
        store.evictElapsed(p, 300L);
        assertTrue(store.ready(p, CooldownStore.key(0, 2), 300L));
    }

    @Test
    void evictElapsedAllPlayersSweepsElapsedButKeepsLive() {
        UUID q = UUID.randomUUID();
        store.arm(p, 1L, 0L, 40);   // expires at 40
        store.arm(q, 1L, 0L, 200);  // expires at 200
        store.evictElapsed(100L);
        assertTrue(store.ready(p, 1L, 100L));  // p elapsed → gone
        assertFalse(store.ready(q, 1L, 100L)); // q still live
    }
}
