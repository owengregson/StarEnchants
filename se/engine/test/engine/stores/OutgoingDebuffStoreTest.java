package engine.stores;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The ADR-0049 Weaken/Destruction outgoing debuff: non-stacking (max, never sum) with a monotone expiry. */
class OutgoingDebuffStoreTest {

    private final OutgoingDebuffStore store = new OutgoingDebuffStore();

    @Test
    void activeWithinWindowThenElapses() {
        UUID player = UUID.randomUUID();
        store.weaken(player, 15.0, 0L, 100);
        assertEquals(15.0, store.active(player, 99L));
        assertEquals(0.0, store.active(player, 100L));
    }

    @Test
    void reDebuffNeverSumsAndKeepsStrongerPercent() {
        UUID player = UUID.randomUUID();
        store.weaken(player, 15.0, 0L, 100);
        store.weaken(player, 10.0, 0L, 100); // a second attacker — must NOT compound to 25
        assertEquals(15.0, store.active(player, 50L), "non-stacking: the stronger percent, never the sum");
    }

    @Test
    void weakerLongerReDebuffExtendsButKeepsStrongerPercent() {
        UUID player = UUID.randomUUID();
        store.weaken(player, 15.0, 0L, 100);
        store.weaken(player, 10.0, 0L, 200);
        assertEquals(15.0, store.active(player, 150L), "stronger percent + later expiry, component-wise");
        assertEquals(0.0, store.active(player, 200L));
    }

    @Test
    void nonPositiveIsNoOp() {
        UUID player = UUID.randomUUID();
        store.weaken(player, 0.0, 0L, 100);
        store.weaken(player, 15.0, 0L, 0);
        assertEquals(0.0, store.active(player, 0L));
    }

    @Test
    void evictElapsedDropsOnlyExpired() {
        UUID live = UUID.randomUUID();
        UUID stale = UUID.randomUUID();
        store.weaken(live, 15.0, 0L, 200);
        store.weaken(stale, 15.0, 0L, 40);
        store.evictElapsed(100L);
        assertEquals(15.0, store.active(live, 100L));
        assertEquals(0.0, store.active(stale, 100L));
    }
}
