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
 * The TURRET_RING registry: the once-only IMPACT claim, and the scoping group a shot carries to its strike
 * (ADR-0074). The group is CAPTURED by the volley chain's own re-arming closure — beside the owner and the
 * profile it already carried — never stored on the emplacement and re-read, because a missed registry read
 * would fail OPEN and fire the owner's whole IMPACT roster.
 */
class TurretCastsTest {

    @AfterEach
    void clean() {
        TurretCasts.clearAll();
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
    void theUngroupedBindStaysUnscoped() {
        UUID shot = UUID.randomUUID();
        TurretCasts.bindShot(shot, UUID.randomUUID());
        assertEquals(-1, TurretCasts.claimImpact(shot).sourceGroup(),
                "a ring armed by an ungrouped ability fires the whole roster, exactly as before the scoping");
    }

    @Test
    void bothHalvesShieldTerrainAndClearTogether() {
        UUID turret = UUID.randomUUID();
        UUID shot = UUID.randomUUID();
        TurretCasts.bindTurret(turret);
        TurretCasts.bindShot(shot, UUID.randomUUID(), 1);
        assertTrue(TurretCasts.neverGriefs(turret));
        assertTrue(TurretCasts.neverGriefs(shot));
        assertFalse(TurretCasts.neverGriefs(UUID.randomUUID()));

        TurretCasts.clearAll();
        assertFalse(TurretCasts.neverGriefs(turret));
        assertFalse(TurretCasts.neverGriefs(shot));
    }
}
