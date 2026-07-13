package engine.sink;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Per-(entity, potion-type) locks: the registry the modern {@code PotionLockListener} consults to DENY a
 * re-application. Keyed by the version-stable type name; guards reject no-op locks; clear/clearAll forget.
 * (Wall-clock expiry self-evicts on read — untested here for the same reason {@link DamageMarksTest} leaves it,
 * a booted-server suite is the timing oracle.)
 */
class LockedPotionsTest {

    private final UUID entity = UUID.randomUUID();

    @AfterEach
    void clean() {
        LockedPotions.clearAll();
    }

    @Test
    void aLockDeniesOnlyItsOwnTypeOnItsOwnEntity() {
        LockedPotions.lock(entity, "HEALTH_BOOST", 60_000L);
        assertTrue(LockedPotions.isLocked(entity, "HEALTH_BOOST"), "the locked type is denied");
        assertFalse(LockedPotions.isLocked(entity, "SPEED"), "a different type on the same entity is not locked");
        assertFalse(LockedPotions.isLocked(UUID.randomUUID(), "HEALTH_BOOST"), "a different entity is not locked");
    }

    @Test
    void anUnlockedEntityDeniesNothing() {
        assertFalse(LockedPotions.isLocked(entity, "HEALTH_BOOST"), "no locks for this entity at all");
    }

    @Test
    void guardsRejectNoOpLocks() {
        LockedPotions.lock(null, "HEALTH_BOOST", 60_000L);
        LockedPotions.lock(entity, null, 60_000L);
        LockedPotions.lock(entity, "HEALTH_BOOST", 0L);  // zero duration
        LockedPotions.lock(entity, "HEALTH_BOOST", -5L); // negative duration
        assertFalse(LockedPotions.isLocked(entity, "HEALTH_BOOST"));
        assertFalse(LockedPotions.isLocked(null, "HEALTH_BOOST"), "a null entity query is safe");
        assertFalse(LockedPotions.isLocked(entity, null), "a null type query is safe");
    }

    @Test
    void clearForgetsAnEntitysLocks() {
        LockedPotions.lock(entity, "HEALTH_BOOST", 60_000L);
        LockedPotions.lock(entity, "SPEED", 60_000L);
        LockedPotions.clear(entity);
        assertFalse(LockedPotions.isLocked(entity, "HEALTH_BOOST"));
        assertFalse(LockedPotions.isLocked(entity, "SPEED"));
    }
}
