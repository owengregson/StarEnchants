package engine.stores;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import engine.stores.MessageThrottleStore.Notice;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MessageThrottleStoreTest {

    private final MessageThrottleStore store = new MessageThrottleStore();

    @Test
    void oneEmitPerWindowPerPlayer() {
        UUID player = UUID.randomUUID();

        assertTrue(store.tryEmit(player, Notice.OUT_OF_SOULS, 0L, 300));
        assertFalse(store.tryEmit(player, Notice.OUT_OF_SOULS, 0L, 300), "the same tick is inside the window it just armed");
        assertFalse(store.tryEmit(player, Notice.OUT_OF_SOULS, 299L, 300), "still inside");
        assertTrue(store.tryEmit(player, Notice.OUT_OF_SOULS, 300L, 300), "half-open: the boundary tick is free");
    }

    @Test
    void throttlesEachPlayerSeparately() {
        // A throttle shared across players would silence everyone the moment one person hit an empty pool.
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        assertTrue(store.tryEmit(a, Notice.OUT_OF_SOULS, 0L, 300));
        assertTrue(store.tryEmit(b, Notice.OUT_OF_SOULS, 0L, 300));
        assertFalse(store.tryEmit(a, Notice.OUT_OF_SOULS, 10L, 300));
    }

    @Test
    void throttlesEachNoticeFamilySeparately() {
        // A shared window would let one empty-pool line swallow the rebate line that explains a blocked proc,
        // and at very different cadences (15s vs 2s) whichever armed first would silence the other outright.
        UUID player = UUID.randomUUID();

        assertTrue(store.tryEmit(player, Notice.OUT_OF_SOULS, 0L, 300));
        assertTrue(store.tryEmit(player, Notice.REBATE, 0L, 40));
        assertFalse(store.tryEmit(player, Notice.REBATE, 39L, 40));
        assertTrue(store.tryEmit(player, Notice.REBATE, 40L, 40));
        assertFalse(store.tryEmit(player, Notice.OUT_OF_SOULS, 40L, 300), "the soul window is untouched by rebates");
    }

    @Test
    void aNonPositiveThrottleStillArmsATick() {
        // Never let a zero throttle mean "unlimited": the caller's floor is one tick, not none.
        UUID player = UUID.randomUUID();

        assertTrue(store.tryEmit(player, Notice.OUT_OF_SOULS, 0L, 0));
        assertFalse(store.tryEmit(player, Notice.OUT_OF_SOULS, 0L, 0));
        assertTrue(store.tryEmit(player, Notice.OUT_OF_SOULS, 1L, 0));
    }

    @Test
    void clearFreesEveryFamilysWindow() {
        UUID player = UUID.randomUUID();
        store.tryEmit(player, Notice.OUT_OF_SOULS, 0L, 300);
        store.tryEmit(player, Notice.REBATE, 0L, 40);

        store.clear(player);

        assertTrue(store.tryEmit(player, Notice.OUT_OF_SOULS, 0L, 300));
        assertTrue(store.tryEmit(player, Notice.REBATE, 0L, 40));
    }
}
