package engine.stores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SoulModeStoreTest {

    private final SoulModeStore store = new SoulModeStore();
    private final UUID p = UUID.randomUUID();

    @Test
    void noGemIsActiveByDefault() {
        assertFalse(store.isActive(p));
        assertEquals(Optional.empty(), store.active(p));
    }

    @Test
    void activateThenReadRoundTrips() {
        UUID gem = UUID.randomUUID();
        store.activate(p, gem);
        assertTrue(store.isActive(p));
        assertEquals(Optional.of(gem), store.active(p));
    }

    @Test
    void activatingAnotherGemReplacesTheActiveOne() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        store.activate(p, first);
        store.activate(p, second);
        assertEquals(Optional.of(second), store.active(p)); // at most one active gem
    }

    @Test
    void deactivateSwitchesSoulModeOff() {
        UUID gem = UUID.randomUUID();
        store.activate(p, gem);
        store.deactivate(p);
        assertFalse(store.isActive(p));
        assertEquals(Optional.empty(), store.active(p));
    }

    @Test
    void deactivateWithNoActiveGemIsANoOp() {
        store.deactivate(p); // nothing was active
        assertFalse(store.isActive(p));
    }

    @Test
    void playersAreIndependent() {
        UUID q = UUID.randomUUID();
        UUID gemP = UUID.randomUUID();
        UUID gemQ = UUID.randomUUID();
        store.activate(p, gemP);
        store.activate(q, gemQ);
        assertEquals(Optional.of(gemP), store.active(p));
        assertEquals(Optional.of(gemQ), store.active(q));
    }

    @Test
    void clearBehavesLikeDeactivateForOnePlayer() {
        UUID q = UUID.randomUUID();
        store.activate(p, UUID.randomUUID());
        store.activate(q, UUID.randomUUID());
        store.clear(p);
        assertFalse(store.isActive(p));
        assertTrue(store.isActive(q)); // other player untouched
    }

    @Test
    void clearAllForgetsEveryone() {
        UUID q = UUID.randomUUID();
        store.activate(p, UUID.randomUUID());
        store.activate(q, UUID.randomUUID());
        store.clearAll();
        assertFalse(store.isActive(p));
        assertFalse(store.isActive(q));
    }
}
