package feature.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import engine.stores.KnockbackControlStore;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

/**
 * Pins the pure KNOCKBACK_CONTROL → reference-plugin-velocity DECISION of {@link MentalKnockbackBridge}: no flag
 * leaves that plugin's vector untouched, a non-positive multiplier zeroes the knockback (a full cancel, not a
 * "let vanilla stand" event-cancel), and a positive multiplier scales it — matching
 * {@link KnockbackControlStore} semantics so SE behaves identically whether knockback comes from that plugin or
 * vanilla. The reflective registration side is verified on a packet/anticheat reference-plugin server out-of-matrix (docs/decisions/0026).
 */
class MentalKnockbackBridgeTest {

    @Test
    void noFlagLeavesMentalVectorUntouched() {
        // KnockbackControlStore.NONE (NaN) means "no active control" — SE must not write anything back.
        assertNull(MentalKnockbackBridge.controlled(KnockbackControlStore.NONE, new Vector(1, 0.4, 1)));
    }

    @Test
    void zeroMultiplierCancelsToZeroVector() {
        assertEquals(new Vector(0, 0, 0), MentalKnockbackBridge.controlled(0.0, new Vector(0.8, 0.4, -0.8)));
    }

    @Test
    void negativeMultiplierAlsoZeroes() {
        // A control is clamped at 0 in the store, but be defensive: anything <= 0 is a full cancel.
        assertEquals(new Vector(0, 0, 0), MentalKnockbackBridge.controlled(-1.0, new Vector(1, 1, 1)));
    }

    @Test
    void positiveMultiplierScales() {
        assertEquals(new Vector(0.4, 0.2, -0.4), MentalKnockbackBridge.controlled(0.5, new Vector(0.8, 0.4, -0.8)));
        assertEquals(new Vector(1.6, 0.8, -1.6), MentalKnockbackBridge.controlled(2.0, new Vector(0.8, 0.4, -0.8)));
    }

    @Test
    void doesNotMutateTheInputVector() {
        Vector input = new Vector(0.8, 0.4, -0.8);
        MentalKnockbackBridge.controlled(2.0, input);
        assertEquals(new Vector(0.8, 0.4, -0.8), input, "the input vector must be left unchanged");
    }
}
