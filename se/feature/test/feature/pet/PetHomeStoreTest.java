package feature.pet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The dig-home store (ADR-0061): tick-expiry, the generation guard, and every teardown path. */
class PetHomeStoreTest {

    private final PetHomeStore store = new PetHomeStore();
    private final UUID player = UUID.randomUUID();
    private final UUID world = UUID.randomUUID();

    @Test
    void liveUntilExpiryTickThenLazilyEvicted() {
        store.arm(player, world, 1.5, 64.0, -3.25, 90.0f, 10.0f, 50.0, 100);

        PetHomeStore.Home home = store.get(player, 99);
        assertNotNull(home);
        // distinct values per axis so a transposed coordinate fails loudly
        assertEquals(world, home.worldId());
        assertEquals(1.5, home.x());
        assertEquals(64.0, home.y());
        assertEquals(-3.25, home.z());
        assertEquals(90.0f, home.yaw());
        assertEquals(10.0f, home.pitch());
        assertEquals(50.0, home.range());

        assertNull(store.get(player, 100), "the expiry tick itself is closed");
        assertNull(store.get(player, 99), "an elapsed read evicts — the entry is gone even for earlier ticks");
    }

    @Test
    void aReDigReplacesAndItsGenerationGuardsTheStaleExpiry() {
        long first = store.arm(player, world, 0, 0, 0, 0f, 0f, 50.0, 100);
        long second = store.arm(player, world, 7, 7, 7, 0f, 0f, 50.0, 300); // re-dug before the first lapsed

        assertFalse(store.clearIfGeneration(player, first), "the stale expiry task must no-op");
        assertEquals(7.0, store.get(player, 150).x(), "the new window survives its predecessor's task");

        assertTrue(store.clearIfGeneration(player, second));
        assertNull(store.get(player, 150));
        assertFalse(store.clearIfGeneration(player, second), "idempotent");
    }

    @Test
    void inRangeIsTheSharedBoundaryTruth() {
        store.arm(player, world, 1.5, 64.0, -3.25, 90.0f, 10.0f, 50.0, 100);
        PetHomeStore.Home home = store.get(player, 0);

        // d = 50 exactly along +x: the boundary is IN, matching the recall's strict-'>' refusal.
        assertTrue(home.inRange(world, 51.5, 64.0, -3.25));
        assertFalse(home.inRange(world, 51.51, 64.0, -3.25), "just past the range is OUT");
        assertFalse(home.inRange(UUID.randomUUID(), 1.5, 64.0, -3.25),
                "another world is OUT even standing on the home block");
    }

    @Test
    void peekReadsWithoutEvictingSoTheExpiryTaskStillOwnsTheEnd() {
        long generation = store.arm(player, world, 0, 0, 0, 0f, 0f, 50.0, 100);

        assertNotNull(store.peek(player, 99));
        assertNull(store.peek(player, 100), "the expiry tick itself is closed");
        // the exact §5 bug: an evicting read here would have made the scheduled expiry no-op.
        assertTrue(store.clearIfGeneration(player, generation), "peek must not evict — the task still ends it");
    }

    @Test
    void recallQuitAndDisableTeardownDropTheWindow() {
        store.arm(player, world, 0, 0, 0, 0f, 0f, 50.0, 100);
        store.clear(player); // recall consume / death / quit sweep
        assertNull(store.get(player, 1));

        store.arm(player, world, 0, 0, 0, 0f, 0f, 50.0, 100);
        store.clearAll(); // disable stop
        assertNull(store.get(player, 1));
    }
}
