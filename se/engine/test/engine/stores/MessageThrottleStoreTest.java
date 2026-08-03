package engine.stores;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class MessageThrottleStoreTest {

    private final MessageThrottleStore store = new MessageThrottleStore();

    @Test
    void oneEmitPerWindowPerPlayer() {
        UUID player = UUID.randomUUID();

        assertTrue(store.tryEmit(player, 0L, 300));
        assertFalse(store.tryEmit(player, 0L, 300), "the same tick is inside the window it just armed");
        assertFalse(store.tryEmit(player, 299L, 300), "still inside");
        assertTrue(store.tryEmit(player, 300L, 300), "half-open: the boundary tick is free");
    }

    @Test
    void throttlesEachPlayerSeparately() {
        // A throttle shared across players would silence everyone the moment one person hit an empty pool.
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        assertTrue(store.tryEmit(a, 0L, 300));
        assertTrue(store.tryEmit(b, 0L, 300));
        assertFalse(store.tryEmit(a, 10L, 300));
    }

    @Test
    void aNonPositiveThrottleStillArmsATick() {
        // Never let a zero throttle mean "unlimited": the caller's floor is one tick, not none.
        UUID player = UUID.randomUUID();

        assertTrue(store.tryEmit(player, 0L, 0));
        assertFalse(store.tryEmit(player, 0L, 0));
        assertTrue(store.tryEmit(player, 1L, 0));
    }
}
