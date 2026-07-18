package feature.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import engine.sink.DotParkLedger;
import engine.sink.EngineDamage;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import platform.caps.Capabilities;
import platform.caps.Regions;
import platform.sched.Scheduling;
import testfx.RecordingSchedulerBackend;

/**
 * The combo-end / post-combo paced release (ADR-0069): oldest debt first, only when the vanilla hit-window is
 * clear (bounded by a wait cap), attributed to the bucket's live attacker (bare when the handle is gone or an
 * off-region read faults). A re-forming combo re-parks everything; the single-release guard lives in the
 * ledger so a mid-flight clear (the Folia retired-task seam) allows a fresh begin. The loop body is driven
 * tick-by-tick through {@link RecordingSchedulerBackend}.
 */
class ComboDotReleaseTest {

    private RecordingSchedulerBackend backend;
    private DotParkLedger ledger;
    private ComboDotRelease release;

    @BeforeEach
    void setUp() {
        backend = new RecordingSchedulerBackend();
        Scheduling.install(backend);
        ledger = new DotParkLedger();
        release = new ComboDotRelease(ledger, () -> 0L);
    }

    @AfterEach
    void restoreRegions() {
        Regions.install(Capabilities.foliaPresent());
    }

    private static Player victim() {
        Player p = mock(Player.class);
        when(p.getUniqueId()).thenReturn(UUID.randomUUID());
        when(p.isValid()).thenReturn(true);
        when(p.isDead()).thenReturn(false);
        when(p.getNoDamageTicks()).thenReturn(0);        // window clear (0 <= 20/2)
        when(p.getMaximumNoDamageTicks()).thenReturn(20);
        return p;
    }

    private static LivingEntity attacker(boolean live) {
        LivingEntity e = mock(LivingEntity.class);
        when(e.getUniqueId()).thenReturn(UUID.randomUUID());
        when(e.isValid()).thenReturn(live);
        return e;
    }

    /** Park one release-eligible bucket (combo starts, banks the hurt, then ends so the debt is drainable). */
    private void park(UUID victimId, LivingEntity attacker, double amount) {
        ledger.comboStarted(victimId, attacker == null ? null : attacker.getUniqueId(), 0L);
        ledger.tryPark(victimId, attacker, amount, 0L);
        ledger.comboEnded(victimId);
    }

    /** Run the single armed loop body once (RecordingSchedulerBackend records rather than runs). */
    private void runTick() {
        backend.repeating.get(0).task.run();
    }

    @Test
    void releasesOldestBucketWhenWindowClear() {
        Player victim = victim();
        LivingEntity attacker = attacker(true);
        park(victim.getUniqueId(), attacker, 4.0);
        boolean[] framed = {false};
        doAnswer(inv -> {
            framed[0] = EngineDamage.active();
            return null;
        }).when(victim).damage(anyDouble(), any());

        release.begin(victim);
        runTick();

        verify(victim).damage(4.0, attacker);
        assertTrue(framed[0], "the release hurt runs inside the engine-damage frame");
        assertFalse(ledger.hasParked(victim.getUniqueId()));
    }

    @Test
    void waitsWhileWindowArmedThenReleases() {
        Player victim = victim();
        LivingEntity attacker = attacker(true);
        AtomicInteger noDamageTicks = new AtomicInteger(20); // armed (20 > 20/2)
        when(victim.getNoDamageTicks()).thenAnswer(inv -> noDamageTicks.get());
        park(victim.getUniqueId(), attacker, 3.0);

        release.begin(victim);
        runTick();
        runTick();
        runTick();
        verify(victim, never()).damage(anyDouble(), any());

        noDamageTicks.set(0); // window clears
        runTick();
        verify(victim).damage(3.0, attacker);
    }

    @Test
    void windowWaitCapForcesThrough() {
        Player victim = victim();
        LivingEntity attacker = attacker(true);
        when(victim.getNoDamageTicks()).thenReturn(20); // never clears
        park(victim.getUniqueId(), attacker, 2.0);

        release.begin(victim);
        for (int i = 0; i < 40; i++) { // WINDOW_WAIT_CAP_TICKS ticks of waiting
            runTick();
        }
        verify(victim, never()).damage(anyDouble(), any());

        runTick(); // cap exceeded → forced through on the next tick
        verify(victim).damage(2.0, attacker);
    }

    @Test
    void comboReactivationLeavesBucketsParked() {
        Player victim = victim();
        LivingEntity attacker = attacker(true);
        park(victim.getUniqueId(), attacker, 5.0);

        release.begin(victim);
        ledger.comboStarted(victim.getUniqueId(), attacker.getUniqueId(), 0L); // a combo re-forms mid-release
        runTick();

        verify(victim, never()).damage(anyDouble(), any());
        assertTrue(ledger.hasParked(victim.getUniqueId()), "buckets stay parked while a combo is active");
        assertTrue(backend.repeating.get(0).isCancelled(), "the loop stops when nothing is eligible");
        assertTrue(ledger.beginRelease(victim.getUniqueId()), "the release flag was dropped on stop");
    }

    @Test
    void deadVictimClearsAndStops() {
        Player victim = victim();
        LivingEntity attacker = attacker(true);
        when(victim.isDead()).thenReturn(true);
        park(victim.getUniqueId(), attacker, 6.0);

        release.begin(victim);
        runTick();

        verify(victim, never()).damage(anyDouble(), any());
        assertFalse(ledger.hasParked(victim.getUniqueId()), "the dead victim's ledger is cleared");
        assertTrue(backend.repeating.get(0).isCancelled());
    }

    @Test
    void invalidAttackerHandleDegradesToBareHurt() {
        Player victim = victim();
        LivingEntity attacker = attacker(false); // handle gone
        park(victim.getUniqueId(), attacker, 4.0);

        release.begin(victim);
        runTick();

        verify(victim).damage(4.0); // bare, unattributed
        verify(victim, never()).damage(anyDouble(), any());
    }

    @Test
    void throwingAttackerReadDegradesToBareHurt() {
        Regions.install(true); // the off-region fault is the silent Folia branch
        Player victim = victim();
        LivingEntity attacker = mock(LivingEntity.class);
        when(attacker.getUniqueId()).thenReturn(UUID.randomUUID());
        when(attacker.isValid()).thenThrow(new IllegalStateException("cross-region"));
        park(victim.getUniqueId(), attacker, 4.0);

        release.begin(victim);
        runTick(); // must not propagate the fault

        verify(victim).damage(4.0); // bare hurt, no attribution
        verify(victim, never()).damage(anyDouble(), any());
    }

    @Test
    void beginIsIdempotentPerVictim() {
        Player victim = victim();
        park(victim.getUniqueId(), attacker(true), 1.0);

        release.begin(victim);
        release.begin(victim); // already in flight → no second task

        assertEquals(1, backend.repeating.size());
    }

    @Test
    void ledgerClearMidFlightAllowsAFreshBegin() {
        Player victim = victim();
        park(victim.getUniqueId(), attacker(true), 1.0);

        release.begin(victim);
        // Folia retired-task seam: the task stops without running its body, and the quit sweep clears the ledger.
        backend.repeating.get(0).cancel();
        ledger.clear(victim.getUniqueId());
        park(victim.getUniqueId(), attacker(true), 1.0);
        release.begin(victim);

        assertEquals(2, backend.repeating.size(), "a fresh begin arms after the mid-flight clear dropped the flag");
    }
}
