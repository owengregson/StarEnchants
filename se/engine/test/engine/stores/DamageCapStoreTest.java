package engine.stores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The ADR-0049 Diminish cap: the R-QC19 two-step arm (pending factor → priced at the commit) + a one-shot
 *  armed cap consumed exactly once. */
class DamageCapStoreTest {

    private final DamageCapStore store = new DamageCapStore();

    @Test
    void aPendingArmIsPricedAgainstTheHitThatArmedIt() {
        UUID player = UUID.randomUUID();
        store.armPending(player, 0.5, false, 0L, 100, "");

        DamageCapStore.Priced priced = store.price(player, 20.0, 10L);

        assertNotNull(priced);
        assertEquals(10.0, priced.value(), 1e-9, "0.5 x the 20.0 THIS hit committed, not any earlier hit");
        DamageCapStore.Cap cap = store.consumeArmed(player, 11L);
        assertNotNull(cap);
        assertEquals(10.0, cap.value(), 1e-9);
    }

    @Test
    void pricingCarriesTheArmsFeedbackAndReflectFlag() {
        UUID player = UUID.randomUUID();
        store.armPending(player, 0.25, true, 0L, 100, "capped at {damage}");

        DamageCapStore.Priced priced = store.price(player, 8.0, 5L);

        assertNotNull(priced);
        assertEquals("capped at {damage}", priced.feedback(), "the line is announced by the caller, unfilled here");
        DamageCapStore.Cap cap = store.consumeArmed(player, 6L);
        assertNotNull(cap);
        assertTrue(cap.reflectOverflow());
    }

    @Test
    void nothingMaterialisesWithoutAnArmingHit() {
        UUID player = UUID.randomUUID();
        assertNull(store.price(player, 20.0, 0L), "no pending arm: this hit prices nothing");

        store.armPending(player, 0.5, false, 0L, 40, "");
        assertNull(store.price(player, 20.0, 40L), "the window elapsed before any hit landed (half-open)");
        assertNull(store.consumeArmed(player, 41L), "and it left no cap behind");

        store.armPending(player, 0.5, false, 0L, 40, "");
        assertNull(store.price(player, 0.0, 1L), "a hit that committed nothing prices a 0 cap, which arms nothing");
        assertNull(store.consumeArmed(player, 2L));
    }

    @Test
    void aPendingArmIsDroppedByTheNextPricedHit() {
        UUID player = UUID.randomUUID();
        store.armPending(player, 0.5, false, 0L, 100, "");
        store.price(player, 10.0, 1L);
        store.consumeArmed(player, 2L); // the window it opened is spent

        assertNull(store.price(player, 30.0, 3L), "a spent arm never re-prices off a later hit");
    }

    @Test
    void theLastArmOfAWalkIsTheOneThatOpensTheWindow() {
        UUID player = UUID.randomUUID();
        store.armPending(player, 0.25, false, 0L, 100, "");
        store.armPending(player, 0.5, true, 0L, 100, ""); // a second DEFENSE proc in the same walk

        DamageCapStore.Priced priced = store.price(player, 8.0, 1L);

        assertNotNull(priced);
        assertEquals(4.0, priced.value(), 1e-9, "the later arm replaces the earlier one whole — factor and reflect");
        DamageCapStore.Cap cap = store.consumeArmed(player, 2L);
        assertNotNull(cap);
        assertTrue(cap.reflectOverflow());
    }

    @Test
    void armedCapConsumesExactlyOnce() {
        UUID player = UUID.randomUUID();
        store.arm(player, 6.0, false, 0L, 100);
        DamageCapStore.Cap cap = store.consumeArmed(player, 50L);
        assertNotNull(cap);
        assertEquals(6.0, cap.value());
        assertFalse(cap.reflectOverflow());
        assertNull(store.consumeArmed(player, 51L), "a one-shot cap: gone after the first consume");
    }

    @Test
    void reflectOverflowFlagRoundTrips() {
        UUID player = UUID.randomUUID();
        store.arm(player, 6.0, true, 0L, 100);
        DamageCapStore.Cap cap = store.consumeArmed(player, 1L);
        assertNotNull(cap);
        assertTrue(cap.reflectOverflow());
    }

    @Test
    void elapsedCapIsNotConsumed() {
        UUID player = UUID.randomUUID();
        store.arm(player, 6.0, false, 0L, 40);
        assertNull(store.consumeArmed(player, 40L), "the expiry tick counts as elapsed (half-open)");
    }

    @Test
    void nonPositiveValueOrDurationArmsNothing() {
        UUID player = UUID.randomUUID();
        store.arm(player, 0.0, false, 0L, 100);
        store.arm(player, 6.0, false, 0L, 0);
        store.armPending(player, 0.0, false, 0L, 100, "");
        store.armPending(player, 0.5, false, 0L, 0, "");
        assertNull(store.price(player, 20.0, 0L), "neither degenerate arm left anything pending");
        assertNull(store.consumeArmed(player, 0L));
    }

    @Test
    void clearForgetsBothMaps() {
        UUID player = UUID.randomUUID();
        store.armPending(player, 0.5, false, 0L, 100, "");
        store.arm(player, 6.0, false, 0L, 100);
        store.clear(player);
        assertNull(store.consumeArmed(player, 0L));
        assertNull(store.price(player, 20.0, 1L), "the pending arm went with it");
    }
}
