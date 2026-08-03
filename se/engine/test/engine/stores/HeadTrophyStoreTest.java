package engine.stores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** HEAD_TROPHY arms: unexpiring, spent by one consume, and bounded so an uncollected trophy cannot leak forever. */
class HeadTrophyStoreTest {

    private final HeadTrophyStore store = new HeadTrophyStore();

    @Test
    void anArmIsSpentByExactlyOneConsume() {
        UUID victim = UUID.randomUUID();
        store.arm(victim, "Skull of {VICTIM}", "one|two", 0L);
        HeadTrophyStore.Trophy trophy = store.consume(victim);
        assertNotNull(trophy);
        assertEquals("Skull of {VICTIM}", trophy.name());
        assertEquals("one|two", trophy.lore());
        assertNull(store.consume(victim), "the second death gets nothing");
    }

    @Test
    void anArmNeverElapses() {
        UUID victim = UUID.randomUUID();
        store.arm(victim, "n", "l", 0L);
        store.evictElapsed(Long.MAX_VALUE);
        store.evictElapsed(victim, Long.MAX_VALUE);
        // The whole contract is "waits for the death that spends it", however many relogs that takes.
        assertNotNull(store.consume(victim));
    }

    @Test
    void reArmingReplacesWithTheLatestTemplates() {
        UUID victim = UUID.randomUUID();
        store.arm(victim, "first", "", 0L);
        store.arm(victim, "second", "", 10L);
        assertEquals("second", store.consume(victim).name());
    }

    @Test
    void theCapacityBoundDropsTheOldestArmFirst() {
        UUID oldest = UUID.randomUUID();
        store.arm(oldest, "oldest", "", 0L);
        for (int i = 0; i < HeadTrophyStore.CAPACITY; i++) {
            store.arm(UUID.randomUUID(), "n" + i, "", i + 1L);
        }
        // Unexpiring + unbounded would be a leak on a long-lived server; the oldest uncollected one goes.
        assertNull(store.consume(oldest));
    }

    @Test
    void clearForgetsOneVictim() {
        UUID victim = UUID.randomUUID();
        store.arm(victim, "n", "l", 0L);
        store.clear(victim);
        assertNull(store.consume(victim));
    }
}
