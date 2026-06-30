package engine.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The falling-block → IMPACT cast registry: bind / claim-once-on-land / forget-on-miss, owner-gated. */
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
    void nullOwnerIsACosmeticSpawnAndNotTracked() {
        UUID block = UUID.randomUUID();
        FallingBlockCasts.bind(block, null, 1.0); // no owner → no impact, just a cosmetic block
        assertFalse(FallingBlockCasts.isTracked(block));
    }
}
