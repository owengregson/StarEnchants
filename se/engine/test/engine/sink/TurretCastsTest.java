package engine.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The TURRET_RING registry: the once-only IMPACT claim, and the scoping group's trip from the emplacement to
 * the shot that strikes (ADR-0074). A turret OUTLIVES the activation that placed it — its volley task re-reads
 * the owner rather than holding one — so the group has to be readable from the emplacement at each shot, which
 * is why it is stored per turret rather than captured by the task.
 */
class TurretCastsTest {

    @AfterEach
    void clean() {
        TurretCasts.clearAll();
    }

    @Test
    void theRingsGroupIsReadableFromTheEmplacementAtEveryVolley() {
        UUID turret = UUID.randomUUID();
        TurretCasts.bindTurret(turret, 12);
        assertEquals(12, TurretCasts.groupOf(turret));
        // Read twice: the volley re-arms itself on a jittered period, so a one-shot read would scope the first
        // shot and leave every later one firing the owner's whole IMPACT roster.
        assertEquals(12, TurretCasts.groupOf(turret));

        TurretCasts.forgetTurret(turret);
        assertEquals(-1, TurretCasts.groupOf(turret), "a retired emplacement scopes nothing");
        assertEquals(-1, TurretCasts.groupOf(UUID.randomUUID()), "and neither does an unknown entity");
    }

    @Test
    void aShotCarriesItsTurretsGroupToTheStrikeAndPaysExactlyOnce() {
        UUID owner = UUID.randomUUID();
        UUID shot = UUID.randomUUID();
        TurretCasts.bindShot(shot, owner, 12);

        TurretCasts.Impact first = TurretCasts.claimImpact(shot);
        assertNotNull(first);
        assertEquals(owner, first.owner());
        assertEquals(12, first.sourceGroup());
        // An explosive shot damages, then its blast damages again; only the first is the hit the ability paid
        // for, and the row survives the claim so the blast is still recognised as ours.
        assertNull(TurretCasts.claimImpact(shot), "the second delivery pays nothing");
        assertTrue(TurretCasts.neverGriefs(shot), "...but the spent row still shields the terrain");
    }

    @Test
    void anOwnerlessOrUnknownShotClaimsNothing() {
        UUID orphan = UUID.randomUUID();
        TurretCasts.bindShot(orphan, null, 12); // fired by nobody — no actor to run IMPACT against
        assertNull(TurretCasts.claimImpact(orphan));
        assertNull(TurretCasts.claimImpact(UUID.randomUUID()));
        assertNull(TurretCasts.claimImpact(null));
    }

    @Test
    void theUngroupedBindsStayUnscoped() {
        UUID turret = UUID.randomUUID();
        UUID shot = UUID.randomUUID();
        TurretCasts.bindTurret(turret);
        TurretCasts.bindShot(shot, UUID.randomUUID());
        assertEquals(-1, TurretCasts.groupOf(turret));
        assertEquals(-1, TurretCasts.claimImpact(shot).sourceGroup());
    }

    @Test
    void bothHalvesShieldTerrainAndClearTogether() {
        UUID turret = UUID.randomUUID();
        UUID shot = UUID.randomUUID();
        TurretCasts.bindTurret(turret, 1);
        TurretCasts.bindShot(shot, UUID.randomUUID(), 1);
        assertTrue(TurretCasts.neverGriefs(turret));
        assertTrue(TurretCasts.neverGriefs(shot));
        assertFalse(TurretCasts.neverGriefs(UUID.randomUUID()));

        TurretCasts.clearAll();
        assertFalse(TurretCasts.neverGriefs(turret));
        assertFalse(TurretCasts.neverGriefs(shot));
    }
}
