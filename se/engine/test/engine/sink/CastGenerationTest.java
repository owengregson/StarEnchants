package engine.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * R-QC58, the reload swap: all four IMPACT-scope carriers hold an INTERNED group id, and a reload re-interns
 * the table — so a cast armed before the swap names a different group after it. Each stamps the generation it
 * was bound under and DROPS at consume when that is stale.
 *
 * <p>Dropping, not unscoping: an unscoped payload fires the owner's whole IMPACT roster, which is exactly the
 * over-firing the scoping was added to stop. A dropped cast loses the tail of one field the operator reloaded
 * out from under.
 */
class CastGenerationTest {

    private static final UUID OWNER = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        CastGeneration.generation(7);
    }

    @AfterEach
    void tearDown() {
        CastGeneration.generation(0);
        FallingBlockCasts.clearAll();
        PetSummons.clearAll();
        TurretCasts.clearAll();
    }

    @Test
    void aFallingBlockCastFromTheOldSnapshotIsDropped() {
        UUID block = UUID.randomUUID();
        FallingBlockCasts.bind(block, OWNER, UUID.randomUUID(), 4.0, 0, 0, 12);
        assertNotNull(FallingBlockCasts.onLand(block), "same generation: the landing still fires its IMPACT");

        UUID stale = UUID.randomUUID();
        FallingBlockCasts.bind(stale, OWNER, UUID.randomUUID(), 4.0, 0, 0, 12);
        CastGeneration.generation(8); // /se reload published a fresh snapshot mid-flight
        assertNull(FallingBlockCasts.onLand(stale));
    }

    @Test
    void aTrackedSummonFromTheOldSnapshotReadsAsUntracked() {
        UUID summon = UUID.randomUUID();
        SummonFlags flags = SummonFlags.NONE.withSourceGroup(12).withStrike(false, false);
        PetSummons.bind(summon, flags);
        assertNotNull(PetSummons.flags(summon));

        CastGeneration.generation(8);
        assertNull(PetSummons.flags(summon), "a courier cannot fire a group id that no longer means what it did");
    }

    @Test
    void aTurretShotFromTheOldSnapshotIsSpentWithoutPaying() {
        UUID shot = UUID.randomUUID();
        TurretCasts.bindShot(shot, OWNER, 12, CastGeneration.current());
        assertEquals(12, TurretCasts.claimImpact(shot).sourceGroup());

        UUID stale = UUID.randomUUID();
        TurretCasts.bindShot(stale, OWNER, 12, CastGeneration.current());
        CastGeneration.generation(8); // the volley chain captured 7 at arm and keeps firing
        assertNull(TurretCasts.claimImpact(stale));
        assertNull(TurretCasts.claimImpact(stale), "and it stays spent, so a second strike cannot re-claim it");
    }
}
