package engine.stores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The ADR-0049 gank window: distinct-attacker counting + 1-based first-seen ordering over a 200-tick window,
 * evicted lazily. Drives Aegis/Anti-Gank ("damage from attackers beyond the first N is reduced").
 */
class RecentAttackersStoreTest {

    private final RecentAttackersStore store = new RecentAttackersStore();

    @Test
    void countsDistinctAttackersInWindow() {
        UUID victim = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        store.record(victim, a, 0L);
        store.record(victim, a, 5L); // same attacker again — still one distinct
        store.record(victim, b, 10L);
        assertEquals(2, store.distinctCount(victim, 10L));
    }

    @Test
    void indexIsOneBasedFirstSeenOrder() {
        UUID victim = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        store.record(victim, first, 0L);
        store.record(victim, second, 1L);
        store.record(victim, first, 2L); // a re-hit must NOT move first to the back
        assertEquals(1, store.indexOf(victim, first, 2L));
        assertEquals(2, store.indexOf(victim, second, 2L));
        assertEquals(0, store.indexOf(victim, UUID.randomUUID(), 2L), "an unseen attacker has index 0");
    }

    @Test
    void windowEvictsElapsedAttackersHalfOpen() {
        UUID victim = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        store.record(victim, a, 0L);
        assertEquals(1, store.distinctCount(victim, 199L), "still inside the 200-tick window");
        assertEquals(0, store.distinctCount(victim, 200L), "the 200th tick counts as elapsed (half-open)");
    }

    @Test
    void reappearingAfterExpiryLandsAtTheBack() {
        UUID victim = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        store.record(victim, a, 0L);
        store.record(victim, b, 210L); // a has expired by now
        store.record(victim, a, 211L); // a re-appears as a fresh, last-seen attacker
        assertEquals(1, store.indexOf(victim, b, 211L));
        assertEquals(2, store.indexOf(victim, a, 211L));
    }

    @Test
    void latestPicksTheMaxTickAttackerNotFirstSeen() {
        UUID victim = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        store.record(victim, a, 10L);
        store.record(victim, b, 20L);
        store.record(victim, a, 30L); // first-seen order stays A,B — latest must go by tick
        RecentAttackersStore.LastAttacker last = store.latest(victim, 35L);
        assertNotNull(last);
        assertEquals(a, last.attacker());
        assertEquals(30L, last.tick());
    }

    @Test
    void latestIsNullWhenEmptyOrExpired() {
        UUID victim = UUID.randomUUID();
        assertNull(store.latest(UUID.randomUUID(), 0L), "an unknown victim has no latest attacker");
        store.record(victim, UUID.randomUUID(), 0L);
        assertNotNull(store.latest(victim, 199L), "still inside the 200-tick window");
        assertNull(store.latest(victim, 200L), "the 200th tick counts as elapsed (half-open, shared with evict)");
    }

    @Test
    void clearForgetsTheVictim() {
        UUID victim = UUID.randomUUID();
        store.record(victim, UUID.randomUUID(), 0L);
        store.clear(victim);
        assertEquals(0, store.distinctCount(victim, 0L));
    }
}
