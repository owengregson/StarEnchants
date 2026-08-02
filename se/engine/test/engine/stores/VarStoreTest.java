package engine.stores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class VarStoreTest {

    private final VarStore store = new VarStore();
    private final UUID p = UUID.randomUUID();

    @Test
    void unsetVariableReadsNull() {
        assertNull(store.get(p, "rage", 0L));
    }

    @Test
    void setAndGetRoundTrip() {
        store.set(p, "rage", "1", 0L, 0);
        assertEquals("1", store.get(p, "rage", 0L));
    }

    @Test
    void namesAreCaseInsensitive() {
        store.set(p, "Rage", "7", 0L, 0);
        assertEquals("7", store.get(p, "rage", 0L)); // %rage% reads what SET_VAR:Rage wrote
        assertEquals("7", store.get(p, "RAGE", 0L));
    }

    @Test
    void timedVariableEvictsLazilyAtExpiry() {
        store.set(p, "buff", "on", 100L, 40);
        assertEquals("on", store.get(p, "buff", 139L));
        assertNull(store.get(p, "buff", 140L));
    }

    @Test
    void zeroTtlNeverExpires() {
        store.set(p, "perm", "x", 100L, 0);
        assertEquals("x", store.get(p, "perm", Long.MAX_VALUE - 1));
    }

    @Test
    void nullValueStoresEmptyString() {
        store.set(p, "blank", null, 0L, 0);
        assertEquals("", store.get(p, "blank", 0L));
    }

    @Test
    void invertFromUnsetGivesOne() {
        store.invert(p, "flag", 0L);
        assertEquals("1", store.get(p, "flag", 0L));
    }

    @Test
    void invertTogglesOneToZeroAndBack() {
        store.set(p, "flag", "1", 0L, 0);
        store.invert(p, "flag", 0L);
        assertEquals("0", store.get(p, "flag", 0L));
        store.invert(p, "flag", 0L);
        assertEquals("1", store.get(p, "flag", 0L));
    }

    @Test
    void invertOfNonNumericGivesOne() {
        store.set(p, "flag", "abc", 0L, 0); // non-numeric parses as 0 → invert → "1"
        store.invert(p, "flag", 0L);
        assertEquals("1", store.get(p, "flag", 0L));
    }

    @Test
    void invertPreservesRemainingTtl() {
        store.set(p, "flag", "1", 100L, 40); // expires at 140
        store.invert(p, "flag", 120L);
        assertEquals("0", store.get(p, "flag", 139L));
        assertNull(store.get(p, "flag", 140L)); // the original expiry is kept, not extended
    }

    // ── TARGET_VAR: counter mode + non-player carriers. The store was always UUID-keyed, so a mob carrier
    // needs no new map — only that nothing in the read/write path assumes the UUID belongs to a player.

    @Test
    void incrementAccumulatesFromUnset() {
        store.increment(p, "bleedstacks", 1, 0, 0L, 0);
        store.increment(p, "bleedstacks", 1, 0, 0L, 0);
        assertEquals("2", store.get(p, "bleedstacks", 0L));
    }

    @Test
    void incrementUsesItsStep() {
        store.increment(p, "charge", 5, 0, 0L, 0);
        store.increment(p, "charge", 3, 0, 0L, 0);
        assertEquals("8", store.get(p, "charge", 0L));
    }

    @Test
    void incrementPinsAtTheCap() {
        for (int i = 0; i < 40; i++) {
            store.increment(p, "stacks", 1, 20, 0L, 0);
        }
        assertEquals("20", store.get(p, "stacks", 0L));
    }

    @Test
    void aZeroCapIsUncapped() {
        for (int i = 0; i < 30; i++) {
            store.increment(p, "stacks", 1, 0, 0L, 0);
        }
        assertEquals("30", store.get(p, "stacks", 0L));
    }

    @Test
    void incrementPreservesRemainingTtlLikeInvert() {
        store.increment(p, "stacks", 1, 0, 100L, 40); // expires at 140
        store.increment(p, "stacks", 1, 0, 120L, 40);
        assertEquals("2", store.get(p, "stacks", 139L));
        assertNull(store.get(p, "stacks", 140L), "a re-stack must not extend the window");
    }

    @Test
    void incrementRestartsAfterTheValueExpires() {
        store.increment(p, "stacks", 1, 0, 0L, 10);
        store.increment(p, "stacks", 1, 0, 20L, 10); // the first elapsed → counts from 0 again
        assertEquals("1", store.get(p, "stacks", 25L));
    }

    @Test
    void incrementOnANonNumericValueRestartsFromZero() {
        store.set(p, "stacks", "abc", 0L, 0);
        store.increment(p, "stacks", 1, 0, 0L, 0);
        assertEquals("1", store.get(p, "stacks", 0L));
    }

    @Test
    void aMobCarrierIsJustAnotherUuid() {
        UUID mob = UUID.randomUUID();
        store.increment(mob, "bleedstacks", 1, 0, 0L, 0);
        assertEquals("1", store.get(mob, "bleedstacks", 0L));
        assertNull(store.get(p, "bleedstacks", 0L), "carriers never share a namespace");
        store.clear(mob);
        assertNull(store.get(mob, "bleedstacks", 0L), "a dead carrier's vars are forgettable");
    }

    @Test
    void clearForgetsOnePlayerAndClearAllForgetsEveryone() {
        UUID q = UUID.randomUUID();
        store.set(p, "v", "1", 0L, 0);
        store.set(q, "v", "1", 0L, 0);
        store.clear(p);
        assertNull(store.get(p, "v", 0L));
        assertEquals("1", store.get(q, "v", 0L));
        store.clearAll();
        assertNull(store.get(q, "v", 0L));
    }
}
