package engine.stores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** ADR-0071 HIT_TEMPO: the holder's tempo window + per-(victim, holder) stolen-interval gating. */
class HitTempoStoreTest {

    private static final double EPS = 1e-9;
    private final HitTempoStore store = new HitTempoStore();

    @Test
    void windowCarriesModelAndFactor() {
        UUID u = UUID.randomUUID();
        store.arm(u, 100L, 40, 0, 0.25);
        HitTempoStore.Window w = store.window(u, 139L);
        assertNotNull(w);
        assertEquals(0, w.model());
        assertEquals(0.25, w.damageFactor(), EPS);
        assertNull(store.window(u, 140L), "the expiry tick counts as elapsed (half-open, lazily evicted)");
    }

    @Test
    void stolenBlocksOnlyThirdParties() {
        UUID v = UUID.randomUUID();
        UUID h = UUID.randomUUID();
        UUID x = UUID.randomUUID();
        store.stampStolen(v, h, 50L);
        assertTrue(store.stolenBlocks(v, x, 49L), "a third party is gated inside the stolen span");
        assertFalse(store.stolenBlocks(v, h, 49L), "the striking holder keeps their own cadence");
        assertFalse(store.stolenBlocks(v, x, 50L), "past the span nobody is gated");
    }

    @Test
    void twoHoldersKeepIndependentIntervals() {
        UUID v = UUID.randomUUID();
        UUID h1 = UUID.randomUUID();
        UUID h2 = UUID.randomUUID();
        UUID x = UUID.randomUUID();
        store.stampStolen(v, h1, 50L);
        store.stampStolen(v, h2, 60L);
        // At 49 both holders pass their own hits; a third party is gated.
        assertFalse(store.stolenBlocks(v, h1, 49L));
        assertFalse(store.stolenBlocks(v, h2, 49L));
        assertTrue(store.stolenBlocks(v, x, 49L));
        // At 55 h1's interval lapsed (until 50) but h2's is live (until 60): h2 passes, h1 is now gated.
        assertFalse(store.stolenBlocks(v, h2, 55L));
        assertTrue(store.stolenBlocks(v, h1, 55L));
        // At 60 both intervals lapsed: nobody is gated.
        assertFalse(store.stolenBlocks(v, x, 60L));
    }

    @Test
    void clearDropsHolderAndVictimSides() {
        UUID u = UUID.randomUUID();
        UUID h = UUID.randomUUID();
        UUID x = UUID.randomUUID();
        store.arm(u, 0L, 40, 0, 0.25);   // u as holder
        store.stampStolen(u, h, 50L);    // u as victim
        assertNotNull(store.window(u, 10L));
        assertTrue(store.stolenBlocks(u, x, 10L));
        store.clear(u);
        assertNull(store.window(u, 10L));
        assertFalse(store.stolenBlocks(u, x, 10L));
    }
}
