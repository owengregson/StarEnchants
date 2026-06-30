package engine.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The falling-block → IMPACT cast registry: bind / claim-once-on-land / forget-on-miss. EVERY cosmetic block is
 * tracked (so the listener cancels its placement); the owner — which may be null — drives the IMPACT abilities.
 */
class FallingBlockCastsTest {

    @AfterEach
    void clean() {
        FallingBlockCasts.clearAll();
    }

    @Test
    void bindThenLandReturnsTheCastOnceThenNothing() {
        UUID block = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        FallingBlockCasts.bind(block, owner, 7.0);
        assertTrue(FallingBlockCasts.isTracked(block));

        FallingBlockCasts.Cast cast = FallingBlockCasts.onLand(block);
        assertEquals(owner, cast.owner());
        assertEquals(7.0, cast.damage());
        assertFalse(FallingBlockCasts.isTracked(block)); // unbound after landing
        assertNull(FallingBlockCasts.onLand(block));      // a second landing of the same block claims nothing
    }

    @Test
    void forgetUnbindsAMissedBlock() {
        UUID block = UUID.randomUUID();
        FallingBlockCasts.bind(block, UUID.randomUUID(), 1.0);
        FallingBlockCasts.forget(block);
        assertFalse(FallingBlockCasts.isTracked(block));
        assertNull(FallingBlockCasts.onLand(block));
    }

    @Test
    void nullOwnerIsTrackedForCancellationButCarriesNoOwner() {
        UUID block = UUID.randomUUID();
        // An owner-less cosmetic (e.g. environment-fired): still tracked so the listener cancels its placement —
        // a FALLING_BLOCK must never stick — but it carries a null owner so no IMPACT fires.
        FallingBlockCasts.bind(block, null, 1.0);
        assertTrue(FallingBlockCasts.isTracked(block));

        FallingBlockCasts.Cast cast = FallingBlockCasts.onLand(block);
        assertNull(cast.owner());
        assertEquals(1.0, cast.damage());
    }
}
