package engine.stores;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The rage-stack store lifecycle: set stamps stacks + a tick, reads default clean, and clear forgets a player. */
class RageStackStoreTest {

    private final UUID player = UUID.randomUUID();

    @Test
    void setStampsStacksAndTickAndReadsBackByPlayer() {
        RageStackStore store = new RageStackStore();
        store.set(player, 4, 200L);
        assertEquals(4, store.current(player));
        assertEquals(200L, store.lastTick(player));

        store.set(player, 6, 260L); // a later hit replaces both
        assertEquals(6, store.current(player));
        assertEquals(260L, store.lastTick(player));
    }

    @Test
    void unsetPlayerReadsZeroAndTheNeverLiveSentinel() {
        RageStackStore store = new RageStackStore();
        assertEquals(0, store.current(player));
        assertEquals(Long.MIN_VALUE, store.lastTick(player)); // never a real armed tick, so a probe never fires
    }

    @Test
    void negativeStacksAreClampedToZero() {
        RageStackStore store = new RageStackStore();
        store.set(player, -3, 10L);
        assertEquals(0, store.current(player));
    }

    @Test
    void clearForgetsThePlayer() {
        RageStackStore store = new RageStackStore();
        store.set(player, 5, 10L);
        store.clear(player);
        assertEquals(0, store.current(player));
        assertEquals(Long.MIN_VALUE, store.lastTick(player));
    }
}
