package engine.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
 * The ADR-0071 CONVERT_SUMMON bat-cloud TURN: a Bell converts an enemy swarm by MEMBERSHIP ({@code
 * turnByBat}), flipping the cloud to permanently target its former owner in place — one flip per cloud,
 * the ringer's own cloud spared, noteHit stamps ignored once turned, and the published pillar riding the
 * former owner's own position. UUID matching only (no bat position read — the Folia fail-closed trap).
 */
class SwarmCloudsTurnTest {

    private final RecordingSchedulerBackend backend = new RecordingSchedulerBackend();
    private SchedulerBackend previous;
    private final long[] now = {100L};
    private final UUID enemyId = UUID.randomUUID();  // the swarm's former owner
    private final UUID ringerId = UUID.randomUUID(); // the Bell ringer
    private Player enemy;

    @BeforeEach
    void setUp() {
        previous = Scheduling.isInitialized() ? Scheduling.backend() : null;
        Scheduling.install(backend);
        enemy = mockPlayer(enemyId, at(5, 64, 7, 0f));
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

    private static Player mockPlayer(UUID id, Location loc) {
        Player p = mock(Player.class);
        when(p.getUniqueId()).thenReturn(id);
        when(p.isValid()).thenReturn(true);
        when(p.getLocation()).thenReturn(loc);
        return p;
    }

    private static Entity bat(UUID id) {
        Entity e = mock(Entity.class);
        when(e.getUniqueId()).thenReturn(id);
        when(e.isValid()).thenReturn(true);
        return e;
    }

    private void armEnemy() {
        SwarmClouds.arm(enemy, 16.0, 300, () -> now[0], () -> null);
    }

    private RecordingSchedulerBackend.Repeat publisher() {
        return backend.repeating.get(backend.repeating.size() - 1);
    }

    private void drive() {
        publisher().task.run();
    }

    @Test
    void turnByBatTurnsOnlyForeignClouds() {
        armEnemy();
        UUID enemyBat = UUID.randomUUID();
        SwarmClouds.track(enemyId, bat(enemyBat));
        Player ringer = mockPlayer(ringerId, at(0, 64, 0, 0f));
        SwarmClouds.arm(ringer, 16.0, 300, () -> now[0], () -> null);
        UUID ringerBat = UUID.randomUUID();
        SwarmClouds.track(ringerId, bat(ringerBat));

        assertNull(SwarmClouds.turnByBat(ringerId, ringerBat), "the ringer's own cloud never turns on them");
        assertEquals(enemyId, SwarmClouds.turnByBat(ringerId, enemyBat), "the enemy cloud turns → its former owner");
    }

    @Test
    void turnByBatCountsEachCloudOnce() {
        armEnemy();
        UUID batA = UUID.randomUUID();
        UUID batB = UUID.randomUUID();
        SwarmClouds.track(enemyId, bat(batA));
        SwarmClouds.track(enemyId, bat(batB));

        assertEquals(enemyId, SwarmClouds.turnByBat(ringerId, batA), "the first bat of the cloud flips it");
        assertNull(SwarmClouds.turnByBat(ringerId, batB), "a second bat of the already-turned cloud counts zero");
    }

    @Test
    void turnByBatIgnoresUntrackedBat() {
        armEnemy();
        SwarmClouds.track(enemyId, bat(UUID.randomUUID()));

        assertNull(SwarmClouds.turnByBat(ringerId, UUID.randomUUID()), "a bat in no cloud turns nothing");
    }

    @Test
    void noteHitIgnoredWhenTurned() {
        armEnemy();
        UUID batId = UUID.randomUUID();
        SwarmClouds.track(enemyId, bat(batId));
        assertEquals(enemyId, SwarmClouds.turnByBat(ringerId, batId));

        // A post-turn attacker stamp must not replace the self-target: the pillar stays on the former owner.
        Entity attacker = mock(Entity.class);
        when(attacker.getUniqueId()).thenReturn(UUID.randomUUID());
        when(attacker.isValid()).thenReturn(true);
        when(attacker.getLocation()).thenReturn(at(3, 64, 0, 0f));
        SwarmClouds.noteHit(enemyId, attacker, now[0]);
        drive();

        SwarmClouds.CloudTarget target = SwarmClouds.target(enemyId, now[0]);
        assertNotNull(target);
        assertEquals(5.0, target.x(), 1e-9, "the pillar sits on the FORMER owner, not the attacker");
        assertEquals(8.0, target.z(), 1e-9, "enemy at z=7 facing +Z → the pillar is one block ahead");
    }

    @Test
    void turnedPublishesOwnerPillar() {
        armEnemy();
        UUID batId = UUID.randomUUID();
        SwarmClouds.track(enemyId, bat(batId));
        assertEquals(enemyId, SwarmClouds.turnByBat(ringerId, batId));

        drive();

        SwarmClouds.CloudTarget target = SwarmClouds.target(enemyId, now[0]);
        assertNotNull(target);
        assertEquals(5.0, target.x(), 1e-9);
        assertEquals(64.0, target.y(), 1e-9);
        assertEquals(8.0, target.z(), 1e-9, "yaw 0 faces +Z → the pillar sits one block ahead of the enemy");
    }
}
