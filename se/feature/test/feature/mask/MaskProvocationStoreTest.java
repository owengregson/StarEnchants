package feature.mask;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class MaskProvocationStoreTest {

    private final MaskProvocationStore store = new MaskProvocationStore();
    private final UUID wearer = UUID.randomUUID();
    private final UUID mob = UUID.randomUUID();

    @Test
    void provokedUntilTtlThenReleased() {
        store.provoke(wearer, mob, 100L);
        assertTrue(store.isProvoked(wearer, mob, 100L));
        assertTrue(store.isProvoked(wearer, mob, 100L + MaskProvocationStore.TTL_TICKS - 1)); // still live one tick before
        assertFalse(store.isProvoked(wearer, mob, 100L + MaskProvocationStore.TTL_TICKS));    // half-open: expiry tick is free
    }

    @Test
    void reProvokeExtendsTheWindow() {
        store.provoke(wearer, mob, 0L);
        store.provoke(wearer, mob, MaskProvocationStore.TTL_TICKS - 10); // a fresh hit late in the window
        long stillLive = 2L * MaskProvocationStore.TTL_TICKS - 20;       // past the first window, inside the second
        assertTrue(store.isProvoked(wearer, mob, stillLive));
    }

    @Test
    void mobsAndWearersAreIndependent() {
        UUID otherMob = UUID.randomUUID();
        UUID otherWearer = UUID.randomUUID();
        store.provoke(wearer, mob, 0L);
        assertFalse(store.isProvoked(wearer, otherMob, 0L), "a mob the wearer never hit is not provoked");
        assertFalse(store.isProvoked(otherWearer, mob, 0L), "another wearer's calm is independent");
    }

    @Test
    void clearForgetsOneWearer() {
        store.provoke(wearer, mob, 0L);
        store.clear(wearer);
        assertFalse(store.isProvoked(wearer, mob, 0L));
    }

    @Test
    void clearAllForgetsEveryWearer() {
        UUID otherWearer = UUID.randomUUID();
        store.provoke(wearer, mob, 0L);
        store.provoke(otherWearer, mob, 0L);
        store.clearAll();
        assertFalse(store.isProvoked(wearer, mob, 0L));
        assertFalse(store.isProvoked(otherWearer, mob, 0L));
    }
}
