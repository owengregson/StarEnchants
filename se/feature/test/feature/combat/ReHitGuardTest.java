package feature.combat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.UUID;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Hit identity (§3.7): only the stamped window-opener's re-hit is a duplicate; the relay is per-event identity. */
class ReHitGuardTest {

    private final ReHitGuard guard = new ReHitGuard();
    private final UUID victim = UUID.randomUUID();
    private final UUID attacker = UUID.randomUUID();

    @AfterEach
    void clearRelay() {
        ReHitGuard.clearSkipped();
    }

    @Test
    void sameAttackerWithinTheHorizonIsTheSameHit() {
        guard.stamp(victim, attacker, 100L);
        assertTrue(guard.sameHit(victim, attacker, 108L, 10));
    }

    @Test
    void aDistinctAttackerInsideTheWindowIsNotTheSameHit() {
        guard.stamp(victim, attacker, 100L);
        assertFalse(guard.sameHit(victim, UUID.randomUUID(), 103L, 10)); // the second-attacker gank case
    }

    @Test
    void anUnstampedVictimIsNeverTheSameHit() {
        assertFalse(guard.sameHit(victim, attacker, 5L, 10)); // fire/poison/DoT armed the window, not a stamp
    }

    @Test
    void aStaleStampBeyondTheHorizonIsNotTheSameHit() {
        guard.stamp(victim, attacker, 100L);
        assertFalse(guard.sameHit(victim, attacker, 111L, 10)); // it cannot have opened the CURRENT window
    }

    @Test
    void aNewerLandedHitSupersedesTheOpener() {
        UUID second = UUID.randomUUID();
        guard.stamp(victim, attacker, 100L);
        guard.stamp(victim, second, 105L); // a processed distinct hit re-stamps
        assertTrue(guard.sameHit(victim, second, 106L, 10));
        assertFalse(guard.sameHit(victim, attacker, 106L, 10));
    }

    @Test
    void theSweepNeverDropsLiveStamps() {
        UUID first = UUID.randomUUID();
        guard.stamp(first, attacker, 0L);
        for (int i = 0; i < 400; i++) { // crosses the sweep threshold with everything still live
            guard.stamp(UUID.randomUUID(), attacker, 0L);
        }
        assertTrue(guard.sameHit(first, attacker, 8L, 10));
    }

    @Test
    void theSkipRelayIsPerEventIdentity() {
        EntityDamageByEntityEvent skipped = mock(EntityDamageByEntityEvent.class);
        EntityDamageByEntityEvent other = mock(EntityDamageByEntityEvent.class);
        ReHitGuard.markSkipped(skipped);
        assertTrue(ReHitGuard.skipped(skipped));
        assertFalse(ReHitGuard.skipped(other)); // a stale mark from an earlier event never leaks forward
        ReHitGuard.clearSkipped();
        assertFalse(ReHitGuard.skipped(skipped));
    }
}
