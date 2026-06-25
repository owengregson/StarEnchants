package engine.stores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RepeatStoreTest {

    private final RepeatStore<String> store = new RepeatStore<>();
    private final UUID p = UUID.randomUUID();

    @Test
    void putReturnsEmptyForFirstHandleThenStoresIt() {
        assertEquals(Optional.empty(), store.put(p, 1, "task-a"));
        assertTrue(store.has(p, 1));
    }

    @Test
    void putReplacesAndHandsBackThePreviousHandle() {
        store.put(p, 1, "task-a");
        assertEquals(Optional.of("task-a"), store.put(p, 1, "task-b")); // the ousted handle is returned so the caller can cancel it
        assertTrue(store.has(p, 1));
        assertEquals(Optional.of("task-b"), store.remove(p, 1));
    }

    @Test
    void removeReturnsTheHandleThenForgetsIt() {
        store.put(p, 1, "task-a");
        assertEquals(Optional.of("task-a"), store.remove(p, 1));
        assertFalse(store.has(p, 1));
        assertEquals(Optional.empty(), store.remove(p, 1)); // second remove is a no-op
    }

    @Test
    void hasAndRemoveOnUnknownPairAreNoOps() {
        assertFalse(store.has(p, 99));
        assertEquals(Optional.empty(), store.remove(p, 99));
        assertEquals(Optional.empty(), store.remove(UUID.randomUUID(), 1));
    }

    @Test
    void abilitiesAndPlayersAreIndependent() {
        UUID q = UUID.randomUUID();
        store.put(p, 1, "p-one");
        store.put(p, 2, "p-two");
        store.put(q, 1, "q-one");

        assertEquals(Optional.of("p-one"), store.remove(p, 1));
        assertFalse(store.has(p, 1));
        assertTrue(store.has(p, 2));
        assertTrue(store.has(q, 1)); // other player, same ability id, untouched
    }

    @Test
    void removeAllDrainsOnePlayerAndLeavesOthers() {
        UUID q = UUID.randomUUID();
        store.put(p, 1, "p-one");
        store.put(p, 2, "p-two");
        store.put(q, 1, "q-one");

        List<String> drained = store.removeAll(p);
        assertEquals(2, drained.size());
        assertTrue(drained.contains("p-one"));
        assertTrue(drained.contains("p-two"));
        assertFalse(store.has(p, 1));
        assertFalse(store.has(p, 2));
        assertTrue(store.has(q, 1));

        assertTrue(store.removeAll(p).isEmpty()); // draining an absent player yields nothing
    }

    @Test
    void removeEverythingDrainsAllPlayers() {
        UUID q = UUID.randomUUID();
        store.put(p, 1, "p-one");
        store.put(p, 2, "p-two");
        store.put(q, 1, "q-one");

        List<String> all = store.removeEverything();
        assertEquals(3, all.size());
        assertTrue(all.contains("p-one"));
        assertTrue(all.contains("p-two"));
        assertTrue(all.contains("q-one"));

        assertFalse(store.has(p, 1));
        assertFalse(store.has(q, 1));
        assertTrue(store.removeEverything().isEmpty()); // empty store yields nothing
    }
}
