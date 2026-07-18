package engine.stores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** ADR-0071 BATTERY: arm/bank(up to maxHits)/peek/spend, with the atomic {@code bank()} return. */
class BatteryStoreTest {

    private static final double EPS = 1e-9;
    private final BatteryStore store = new BatteryStore();

    @Test
    void armReplacesResidue() {
        UUID u = UUID.randomUUID();
        store.arm(u, 0.2, 3);
        store.bank(u, 10.0);        // banked 2.0, hitsLeft 2
        store.arm(u, 0.2, 3);       // re-arm wipes the residue
        assertEquals(0.0, store.peek(u), EPS);
        BatteryStore.Core fresh = store.bank(u, 5.0);
        assertEquals(2, fresh.hitsLeft(), "re-arm restored the full hit budget");
    }

    @Test
    void bankFractionAndCap() {
        UUID u = UUID.randomUUID();
        store.arm(u, 0.2, 3);
        BatteryStore.Core c1 = store.bank(u, 7.5);
        assertEquals(1.5, c1.banked(), EPS);
        assertEquals(2, c1.hitsLeft());
        BatteryStore.Core c2 = store.bank(u, 10.0);
        assertEquals(3.5, c2.banked(), EPS);
        assertEquals(1, c2.hitsLeft());
        BatteryStore.Core c3 = store.bank(u, 5.0);
        assertEquals(4.5, c3.banked(), EPS);
        assertEquals(0, c3.hitsLeft());
        assertNull(store.bank(u, 9.0), "the 4th hit is past maxHits — no bank");
        assertEquals(4.5, store.peek(u), EPS);
    }

    @Test
    void bankReturnsNullWhenUnarmed() {
        UUID u = UUID.randomUUID();
        assertNull(store.bank(u, 5.0));
        assertEquals(0.0, store.peek(u), EPS);
    }

    @Test
    void peekDoesNotConsume() {
        UUID u = UUID.randomUUID();
        store.arm(u, 0.2, 3);
        store.bank(u, 10.0);
        assertEquals(2.0, store.peek(u), EPS);
        assertEquals(2.0, store.peek(u), EPS);
        assertTrue(store.armed(u));
    }

    @Test
    void spendReturnsAndClears() {
        UUID u = UUID.randomUUID();
        store.arm(u, 0.2, 3);
        store.bank(u, 7.5);
        store.bank(u, 10.0);
        store.bank(u, 5.0);        // banked 4.5
        assertEquals(4.5, store.spend(u), EPS);
        assertFalse(store.armed(u));
        assertEquals(0.0, store.spend(u), EPS, "a spent core is gone");
    }

    @Test
    void spendAtZeroBankedClears() {
        UUID u = UUID.randomUUID();
        store.arm(u, 0.2, 3);
        assertEquals(0.0, store.spend(u), EPS, "the owner-ruled empty discharge");
        assertFalse(store.armed(u));
    }
}
