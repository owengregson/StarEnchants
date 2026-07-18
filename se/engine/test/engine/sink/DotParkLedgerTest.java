package engine.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import engine.stores.PlayerScoped;
import java.util.List;
import java.util.UUID;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.Test;

/** The ledger math (ADR-0069): park gating, per-attacker sums, drain/restore, the no-mid-combo-release rule, quit clear. */
class DotParkLedgerTest {

    private static LivingEntity attacker(UUID id) {
        LivingEntity e = mock(LivingEntity.class);
        when(e.getUniqueId()).thenReturn(id);
        return e;
    }

    @Test
    void parkRequiresActiveCombo() {
        DotParkLedger ledger = new DotParkLedger();
        UUID victim = UUID.randomUUID();
        LivingEntity a = attacker(UUID.randomUUID());

        assertFalse(ledger.tryPark(victim, a, 1.0, 0L), "no combo state → apply normally");

        ledger.comboStarted(victim, a.getUniqueId(), 100L);
        assertTrue(ledger.tryPark(victim, a, 1.0, 100L), "active combo → park");
        assertTrue(ledger.tryPark(victim, a, 1.0, 100L + DotParkLedger.COMBO_TTL_TICKS), "still inside the TTL belt");
        assertFalse(ledger.tryPark(victim, a, 1.0, 100L + DotParkLedger.COMBO_TTL_TICKS + 1),
                "past the TTL belt the state is stale → apply normally");

        ledger.comboEnded(victim);
        assertFalse(ledger.tryPark(victim, a, 1.0, 100L), "combo ended → apply normally");
    }

    @Test
    void bucketsAccumulatePerAttackerAndMergeSums() {
        DotParkLedger ledger = new DotParkLedger();
        UUID victim = UUID.randomUUID();
        LivingEntity a = attacker(UUID.randomUUID());
        LivingEntity b = attacker(UUID.randomUUID());
        ledger.comboStarted(victim, a.getUniqueId(), 0L);

        assertTrue(ledger.tryPark(victim, a, 2.0, 5L));
        assertTrue(ledger.tryPark(victim, a, 3.5, 8L)); // merges into A's bucket
        assertTrue(ledger.tryPark(victim, b, 7.0, 6L)); // distinct attacker → separate bucket

        List<DotParkLedger.Bucket> fromA = ledger.drainFor(victim, a.getUniqueId());
        assertEquals(1, fromA.size(), "A's two parks are one merged bucket");
        assertEquals(5.5, fromA.get(0).amount(), 1e-9, "amounts sum");
        assertEquals(5L, fromA.get(0).firstParkedTick(), "the FIRST park's tick is kept");

        List<DotParkLedger.Bucket> fromB = ledger.drainFor(victim, b.getUniqueId());
        assertEquals(1, fromB.size());
        assertEquals(7.0, fromB.get(0).amount(), 1e-9, "B is a separate bucket, untouched by A's merge");
    }

    @Test
    void drainForTakesOwnPlusUnattributedOnly() {
        DotParkLedger ledger = new DotParkLedger();
        UUID victim = UUID.randomUUID();
        LivingEntity a = attacker(UUID.randomUUID());
        LivingEntity b = attacker(UUID.randomUUID());
        ledger.comboStarted(victim, a.getUniqueId(), 0L);
        ledger.tryPark(victim, a, 3.0, 1L);
        ledger.tryPark(victim, b, 2.0, 1L);
        ledger.tryPark(victim, null, 1.0, 1L); // UNATTRIBUTED

        List<DotParkLedger.Bucket> drained = ledger.drainFor(victim, a.getUniqueId());
        assertEquals(2, drained.size(), "A's own bucket + the unattributed bucket");
        assertEquals(4.0, drained.stream().mapToDouble(DotParkLedger.Bucket::amount).sum(), 1e-9, "3.0 + 1.0");
        assertTrue(ledger.hasParked(victim), "B's bucket (2.0) is never credited to A — it stays parked");

        List<DotParkLedger.Bucket> fromB = ledger.drainFor(victim, b.getUniqueId());
        assertEquals(1, fromB.size());
        assertEquals(2.0, fromB.get(0).amount(), 1e-9);
        assertFalse(ledger.hasParked(victim), "nothing left once B drains too");
    }

    @Test
    void restoreReinsertsWithOriginalAgeAndMerges() {
        DotParkLedger ledger = new DotParkLedger();
        UUID victim = UUID.randomUUID();
        LivingEntity a = attacker(UUID.randomUUID());
        ledger.comboStarted(victim, a.getUniqueId(), 0L);
        ledger.tryPark(victim, a, 4.0, 5L); // the original debt, tick 5

        List<DotParkLedger.Bucket> drained = ledger.drainFor(victim, a.getUniqueId());
        ledger.tryPark(victim, a, 1.5, 30L); // a fresh A-tick parked after the drain, tick 30
        ledger.restore(victim, drained); // a cancelled flush re-parks the drained debt

        List<DotParkLedger.Bucket> merged = ledger.drainFor(victim, a.getUniqueId());
        assertEquals(1, merged.size(), "restore merges into A's live bucket");
        assertEquals(5.5, merged.get(0).amount(), 1e-9, "4.0 restored + 1.5 fresh");
        assertEquals(5L, merged.get(0).firstParkedTick(), "the OLDER age wins");
    }

    @Test
    void drainOldestEligibleIsNullWhileComboActive() {
        DotParkLedger ledger = new DotParkLedger();
        UUID victim = UUID.randomUUID();
        LivingEntity a = attacker(UUID.randomUUID());
        LivingEntity b = attacker(UUID.randomUUID());

        assertNull(ledger.drainOldestEligible(victim, 0L), "empty → null");

        ledger.comboStarted(victim, a.getUniqueId(), 0L);
        ledger.tryPark(victim, a, 2.0, 1L); // oldest
        ledger.tryPark(victim, b, 3.0, 9L);

        assertNull(ledger.drainOldestEligible(victim, 50L), "combo active → nothing eligible, however old the debt");
        assertEquals(2.0, ledger.drainOldestEligible(victim, DotParkLedger.COMBO_TTL_TICKS + 1L).amount(), 1e-9,
                "past the TTL belt the state is stale → the oldest debt releases first");

        ledger.comboEnded(victim); // ended → the remaining bucket is eligible
        assertEquals(3.0, ledger.drainOldestEligible(victim, 50L).amount(), 1e-9);
        assertNull(ledger.drainOldestEligible(victim, 50L), "drained empty → null");
    }

    @Test
    void releaseGuardIsPerVictimAndDroppedByClear() {
        DotParkLedger ledger = new DotParkLedger();
        UUID v1 = UUID.randomUUID();
        UUID v2 = UUID.randomUUID();

        assertTrue(ledger.beginRelease(v1), "first begin owns the loop");
        assertFalse(ledger.beginRelease(v1), "a second begin is refused while in-flight");
        assertTrue(ledger.beginRelease(v2), "the guard is per-victim");

        ledger.endRelease(v1);
        assertTrue(ledger.beginRelease(v1), "endRelease frees the guard");

        ledger.clear(v1); // the Folia quit-mid-release self-heal seam: clear() drops the in-flight flag
        assertTrue(ledger.beginRelease(v1), "clear frees the guard for a fresh begin");
    }

    @Test
    void clearDropsStateAndBucketsAndReleaseFlag() {
        DotParkLedger ledger = new DotParkLedger();
        UUID victim = UUID.randomUUID();
        LivingEntity a = attacker(UUID.randomUUID());
        ledger.comboStarted(victim, a.getUniqueId(), 0L);
        ledger.tryPark(victim, a, 3.0, 1L);
        ledger.beginRelease(victim);
        assertTrue(ledger.hasParked(victim));

        ledger.clear(victim);

        assertFalse(ledger.hasParked(victim), "buckets dropped");
        assertFalse(ledger.comboActive(victim, 1L), "combo state dropped");
        assertTrue(ledger.beginRelease(victim), "release flag dropped");
        assertFalse(ledger.tryPark(victim, a, 1.0, 1L), "no combo state remains → apply normally");
    }

    @Test
    void playerScopedClearIsTheQuitSweepSeam() {
        DotParkLedger ledger = new DotParkLedger();
        UUID victim = UUID.randomUUID();
        LivingEntity a = attacker(UUID.randomUUID());
        ledger.comboStarted(victim, a.getUniqueId(), 0L);
        ledger.tryPark(victim, a, 3.0, 1L);

        PlayerScoped sweep = ledger; // the single quit-cleanup authority iterates PlayerScoped.clear
        sweep.clear(victim);

        assertFalse(ledger.hasParked(victim));
        assertFalse(ledger.comboActive(victim, 1L));
    }
}
