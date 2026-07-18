package engine.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import engine.stores.RecentAttackersStore;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import platform.sched.SchedulerBackend;
import platform.sched.Scheduling;
import testfx.RecordingSchedulerBackend;

/**
 * The ADR-0068 attacker-cloud state machine: publish/most-recent-wins/window/range-hysteresis/staleness,
 * the fail-closed guarded read, and every teardown path (bats dead, owner dead, deadline, orphan reap,
 * quit scope). The {@link RecordingSchedulerBackend} captures the publisher so each tick is hand-driven.
 */
class SwarmCloudsTest {

    private final RecordingSchedulerBackend backend = new RecordingSchedulerBackend();
    private SchedulerBackend previous;
    private final long[] now = {100L};
    private final UUID ownerId = UUID.randomUUID();
    private Player owner;

    @BeforeEach
    void setUp() {
        previous = Scheduling.isInitialized() ? Scheduling.backend() : null;
        Scheduling.install(backend);
        owner = mockOwner(ownerId, at(0, 64, 0, 0f));
    }

    @AfterEach
    void tearDown() {
        SwarmClouds.clearAll();
        if (previous != null) {
            Scheduling.install(previous);
        }
    }

    private static Location at(double x, double y, double z, float yaw) {
        return new Location(null, x, y, z, yaw, 0f);
    }

    private static Player mockOwner(UUID id, Location loc) {
        Player p = mock(Player.class);
        when(p.getUniqueId()).thenReturn(id);
        when(p.isValid()).thenReturn(true);
        when(p.getLocation()).thenReturn(loc);
        return p;
    }

    private static Entity mockAttacker(Location loc) {
        Entity e = mock(Entity.class);
        when(e.getUniqueId()).thenReturn(UUID.randomUUID());
        when(e.isValid()).thenReturn(true);
        when(e.getLocation()).thenReturn(loc);
        return e;
    }

    private static Entity liveBat() {
        Entity e = mock(Entity.class);
        when(e.isValid()).thenReturn(true);
        return e;
    }

    private void arm() {
        SwarmClouds.arm(owner, 16.0, 300, () -> now[0], () -> null);
    }

    /** Arm + census one live bat, so an empty-census prune can't tear the entry down mid-test. */
    private void armTracked() {
        arm();
        SwarmClouds.track(ownerId, liveBat());
    }

    private RecordingSchedulerBackend.Repeat publisher() {
        return backend.repeating.get(backend.repeating.size() - 1);
    }

    private void drive() {
        publisher().task.run();
    }

    @Test
    void noteHitIsInertUntilArmed() {
        SwarmClouds.noteHit(ownerId, mockAttacker(at(3, 64, 0, 0f)), now[0]);
        assertNull(SwarmClouds.target(ownerId, now[0]), "a hit with no armed cloud must be a no-op");
        SwarmClouds.noteHit(ownerId, mockAttacker(at(3, 64, 0, 0f)), now[0]);
        assertNull(SwarmClouds.target(ownerId, now[0]), "still inert — the empty-map hot-path contract");
    }

    @Test
    void publishesTheFacingPillarForANearbyInWindowAttacker() {
        armTracked();
        Entity attacker = mockAttacker(at(3, 64, 0, 0f));
        SwarmClouds.noteHit(ownerId, attacker, now[0]);
        drive();
        SwarmClouds.CloudTarget target = SwarmClouds.target(ownerId, now[0]);
        assertNotNull(target);
        assertEquals(3.0, target.x(), 1e-9);
        assertEquals(64.0, target.y(), 1e-9);
        assertEquals(1.0, target.z(), 1e-9, "yaw 0 faces +Z — the pillar sits one block ahead");
        assertEquals(now[0], target.stampTick());
        when(attacker.getLocation()).thenReturn(at(3, 64, 0, 90f));
        drive();
        target = SwarmClouds.target(ownerId, now[0]);
        assertNotNull(target);
        assertEquals(2.0, target.x(), 1e-9, "yaw 90 faces -X — the pillar tracked the turn");
        assertEquals(0.0, target.z(), 1e-9);
    }

    @Test
    void mostRecentAttackerWins() {
        armTracked();
        SwarmClouds.noteHit(ownerId, mockAttacker(at(3, 64, 0, 0f)), 5L);
        SwarmClouds.noteHit(ownerId, mockAttacker(at(-5, 64, 2, 0f)), 9L);
        drive();
        SwarmClouds.CloudTarget target = SwarmClouds.target(ownerId, now[0]);
        assertNotNull(target);
        assertEquals(-5.0, target.x(), 1e-9, "the later hit's attacker owns the pillar");
        assertEquals(3.0, target.z(), 1e-9);
    }

    @Test
    void windowExpiryRevertsToNull() {
        armTracked();
        SwarmClouds.noteHit(ownerId, mockAttacker(at(3, 64, 0, 0f)), now[0]);
        drive();
        assertNotNull(SwarmClouds.target(ownerId, now[0]));
        long hitTick = now[0];
        now[0] = hitTick + SwarmClouds.WINDOW_TICKS;
        drive();
        assertNull(SwarmClouds.target(ownerId, now[0]), "the 200-tick window lapsed");
        drive();
        assertNull(SwarmClouds.target(ownerId, now[0]), "and stays lapsed");
    }

    @Test
    void outOfRangeClearsButReacquiresOnReentry() {
        armTracked();
        Entity attacker = mockAttacker(at(20, 64, 0, 0f)); // past even the release gate 17
        SwarmClouds.noteHit(ownerId, attacker, now[0]);
        drive();
        assertNull(SwarmClouds.target(ownerId, now[0]), "20 blocks is not nearby");
        when(attacker.getLocation()).thenReturn(at(3, 64, 0, 0f));
        drive();
        assertNotNull(SwarmClouds.target(ownerId, now[0]), "re-entering within the window re-acquires");
    }

    @Test
    void rangeGateHoldsThroughBoundaryJitter() {
        armTracked();
        Entity attacker = mockAttacker(at(15.8, 64, 0, 0f));
        SwarmClouds.noteHit(ownerId, attacker, now[0]);
        drive();
        assertNotNull(SwarmClouds.target(ownerId, now[0]), "15.8 engages (<= 16)");
        when(attacker.getLocation()).thenReturn(at(16.4, 64, 0, 0f));
        drive();
        assertNotNull(SwarmClouds.target(ownerId, now[0]), "16.4 holds inside the release gate 17 — no flicker");
        when(attacker.getLocation()).thenReturn(at(17.2, 64, 0, 0f));
        drive();
        assertNull(SwarmClouds.target(ownerId, now[0]), "17.2 releases (> 17)");
        when(attacker.getLocation()).thenReturn(at(16.4, 64, 0, 0f));
        drive();
        assertNull(SwarmClouds.target(ownerId, now[0]), "re-acquiring needs <= 16 — the engage/release asymmetry");
    }

    @Test
    void deadOrOfflineAttackerClearsTheSlot() {
        armTracked();
        Entity attacker = mockAttacker(at(3, 64, 0, 0f));
        SwarmClouds.noteHit(ownerId, attacker, now[0]);
        when(attacker.isValid()).thenReturn(false);
        drive();
        assertNull(SwarmClouds.target(ownerId, now[0]));
        when(attacker.isValid()).thenReturn(true); // an in-range revival must NOT resurrect the cleared slot
        drive();
        assertNull(SwarmClouds.target(ownerId, now[0]), "the slot was cleared, not just the target");
    }

    @Test
    void staleTargetIsWithheldFromConsumers() {
        armTracked();
        SwarmClouds.noteHit(ownerId, mockAttacker(at(3, 64, 0, 0f)), now[0]);
        drive();
        long stamp = now[0];
        assertNotNull(SwarmClouds.target(ownerId, stamp + SwarmClouds.STALE_TICKS), "at the horizon: still served");
        assertNull(SwarmClouds.target(ownerId, stamp + SwarmClouds.STALE_TICKS + 1), "past it: withheld");
    }

    @Test
    void crossRegionFaultKeepsLastTargetAndFailsClosed() {
        armTracked();
        Entity attacker = mockAttacker(at(3, 64, 0, 0f));
        when(attacker.getLocation())
                .thenReturn(at(3, 64, 0, 0f))
                .thenThrow(new RuntimeException("cross-region read"));
        SwarmClouds.noteHit(ownerId, attacker, now[0]);
        drive();
        SwarmClouds.CloudTarget first = SwarmClouds.target(ownerId, now[0]);
        assertNotNull(first);
        drive(); // the guarded read faults — swallowed, last target kept
        SwarmClouds.CloudTarget kept = SwarmClouds.target(ownerId, now[0]);
        assertNotNull(kept);
        assertEquals(first, kept, "the fault keeps the last published pillar");
        now[0] = first.stampTick() + SwarmClouds.STALE_TICKS + 1;
        assertNull(SwarmClouds.target(ownerId, now[0]), "STALE_TICKS bounds the lie");
    }

    @Test
    void teardownWhenBatsDieOwnerDiesOrDeadlinePasses() {
        // (a) the census prunes to empty
        arm();
        Entity deadBat = mock(Entity.class);
        when(deadBat.isValid()).thenReturn(false);
        SwarmClouds.track(ownerId, deadBat);
        RecordingSchedulerBackend.Repeat handleA = publisher();
        drive();
        assertTrue(handleA.isCancelled(), "all bats dead -> entry dropped + publisher cancelled");
        SwarmClouds.clearAll();

        // (b) the owner dies/despawns
        Player deadOwner = mockOwner(UUID.randomUUID(), at(0, 64, 0, 0f));
        SwarmClouds.arm(deadOwner, 16.0, 300, () -> now[0], () -> null);
        SwarmClouds.track(deadOwner.getUniqueId(), liveBat());
        when(deadOwner.isValid()).thenReturn(false);
        RecordingSchedulerBackend.Repeat handleB = publisher();
        drive();
        assertTrue(handleB.isCancelled(), "owner invalid -> teardown");
        SwarmClouds.clearAll();

        // (c) the hard deadline passes
        armTracked();
        RecordingSchedulerBackend.Repeat handleC = publisher();
        now[0] += 300 + SwarmClouds.DEADLINE_SLACK_TICKS + 1;
        drive();
        assertTrue(handleC.isCancelled(), "past the ttl+slack deadline -> teardown");
    }

    @Test
    void armReapCancelsOrphanedPublishers() {
        armTracked();
        RecordingSchedulerBackend.Repeat orphaned = publisher();
        now[0] += 300 + SwarmClouds.DEADLINE_SLACK_TICKS + 1; // past A's deadline, publisher never ran
        Player other = mockOwner(UUID.randomUUID(), at(0, 64, 0, 0f));
        SwarmClouds.arm(other, 16.0, 300, () -> now[0], () -> null);
        assertTrue(orphaned.isCancelled(), "the reap must route through drop(), not a bare map remove");
        assertFalse(publisher().isCancelled(), "the fresh cloud's publisher is live");
    }

    @Test
    void seedResolvesThePreSummonAttackerAtItsOriginalTick() {
        UUID attackerId = UUID.randomUUID();
        Entity near = mockAttacker(at(3, 64, 0, 0f));
        when(near.getUniqueId()).thenReturn(attackerId);
        when(owner.getNearbyEntities(anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of(near));
        SwarmClouds.arm(owner, 16.0, 300, () -> now[0],
                () -> new RecentAttackersStore.LastAttacker(attackerId, 42L));
        SwarmClouds.track(ownerId, liveBat());
        drive();
        assertNotNull(SwarmClouds.target(ownerId, now[0]), "the pre-summon attacker was resolved and published");
        now[0] = 42L + SwarmClouds.WINDOW_TICKS;
        drive();
        assertNull(SwarmClouds.target(ownerId, now[0]), "the seed kept the ORIGINAL hit tick — never extends the window");
    }

    @Test
    void quitScopeDropsTheCloud() {
        armTracked();
        SwarmClouds.noteHit(ownerId, mockAttacker(at(3, 64, 0, 0f)), now[0]);
        drive();
        assertNotNull(SwarmClouds.target(ownerId, now[0]));
        RecordingSchedulerBackend.Repeat handle = publisher();
        SwarmClouds.scope().clear(ownerId);
        assertTrue(handle.isCancelled());
        assertNull(SwarmClouds.target(ownerId, now[0]));
    }
}
