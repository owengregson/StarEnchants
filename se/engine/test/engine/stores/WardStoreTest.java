package engine.stores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class WardStoreTest {

    private static final double EPS = 1e-9;

    private final WardStore store = new WardStore();
    private final UUID p = UUID.randomUUID();

    @Test
    void wardedUntilExpiryThenReleased() {
        store.arm(p, WardStore.Type.MOB_TARGET, 100L, 40, 0.0);
        assertTrue(store.isWarded(p, WardStore.Type.MOB_TARGET, 100L));
        assertTrue(store.isWarded(p, WardStore.Type.MOB_TARGET, 139L));
        assertFalse(store.isWarded(p, WardStore.Type.MOB_TARGET, 140L)); // half-open: the expiry tick is free
    }

    @Test
    void typesAreIndependent() {
        store.arm(p, WardStore.Type.INVSEE, 0L, 100, 0.0);
        assertTrue(store.isWarded(p, WardStore.Type.INVSEE, 50L));
        assertFalse(store.isWarded(p, WardStore.Type.NEAR, 50L), "a different type is not warded");
    }

    @Test
    void playersAreIndependent() {
        UUID q = UUID.randomUUID();
        store.arm(p, WardStore.Type.NEAR, 0L, 100, 0.0);
        assertTrue(store.isWarded(p, WardStore.Type.NEAR, 0L));
        assertFalse(store.isWarded(q, WardStore.Type.NEAR, 0L));
    }

    @Test
    void nonPositiveTtlArmsNothing() {
        store.arm(p, WardStore.Type.SPLASH_HEAL, 0L, 0, 50.0);
        store.arm(p, WardStore.Type.SPLASH_HEAL, 0L, -5, 50.0);
        assertFalse(store.isWarded(p, WardStore.Type.SPLASH_HEAL, 0L));
        assertEquals(0.0, store.amount(p, WardStore.Type.SPLASH_HEAL, 0L), EPS);
    }

    @Test
    void amountRidesTheWardAndIsZeroWhenUnwardedOrExpired() {
        store.arm(p, WardStore.Type.SPLASH_HEAL, 0L, 100, 50.0);
        assertEquals(50.0, store.amount(p, WardStore.Type.SPLASH_HEAL, 50L), EPS);
        assertEquals(0.0, store.amount(p, WardStore.Type.SPLASH_HEAL, 100L), EPS); // expired → 0
        assertEquals(0.0, store.amount(p, WardStore.Type.INVSEE, 50L), EPS);       // other type unwarded → 0
    }

    @Test
    void reArmExtendsNeverShortensAndTheWinningArmCarriesItsAmount() {
        store.arm(p, WardStore.Type.SPLASH_HEAL, 0L, 100, 50.0);
        store.arm(p, WardStore.Type.SPLASH_HEAL, 10L, 20, 75.0); // would expire at 30 — must not shorten
        assertTrue(store.isWarded(p, WardStore.Type.SPLASH_HEAL, 50L));
        assertEquals(50.0, store.amount(p, WardStore.Type.SPLASH_HEAL, 50L), EPS); // the live window's amount kept
        assertFalse(store.isWarded(p, WardStore.Type.SPLASH_HEAL, 100L));

        store.arm(p, WardStore.Type.SPLASH_HEAL, 0L, 200, 30.0); // a longer re-arm extends AND re-carries
        assertTrue(store.isWarded(p, WardStore.Type.SPLASH_HEAL, 150L));
        assertEquals(30.0, store.amount(p, WardStore.Type.SPLASH_HEAL, 150L), EPS);
    }

    @Test
    void clearForgetsALiveWard() {
        store.arm(p, WardStore.Type.MOB_TARGET, 0L, 200, 0.0);
        store.clear(p);
        assertFalse(store.isWarded(p, WardStore.Type.MOB_TARGET, 0L));
    }

    @Test
    void clearAllForgetsEveryPlayer() {
        UUID q = UUID.randomUUID();
        store.arm(p, WardStore.Type.INVSEE, 0L, 200, 0.0);
        store.arm(q, WardStore.Type.NEAR, 0L, 200, 0.0);
        store.clearAll();
        assertFalse(store.isWarded(p, WardStore.Type.INVSEE, 0L));
        assertFalse(store.isWarded(q, WardStore.Type.NEAR, 0L));
    }

    @Test
    void wireCodesResolveInDeclaredOrderAndOutOfRangeIsNull() {
        // The Sink passes the ordinal as the wire code (the ImmuneStore.Type contract).
        assertEquals(WardStore.Type.MOB_TARGET, WardStore.Type.of(0));
        assertEquals(WardStore.Type.INVSEE, WardStore.Type.of(1));
        assertEquals(WardStore.Type.NEAR, WardStore.Type.of(2));
        assertEquals(WardStore.Type.SPLASH_HEAL, WardStore.Type.of(3));
        assertEquals(null, WardStore.Type.of(4));
        assertEquals(null, WardStore.Type.of(-1));
    }
}
