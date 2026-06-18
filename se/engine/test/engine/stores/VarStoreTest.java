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
        assertNull(store.get(p, "buff", 140L)); // expiry tick counts as elapsed
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
