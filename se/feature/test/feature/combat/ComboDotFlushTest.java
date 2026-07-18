package feature.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import compile.load.ContentHolder;
import engine.run.AbilityExecutor;
import engine.run.ActorProbe;
import engine.sink.DotParkLedger;
import engine.sink.ModernDispatchSink;
import engine.sink.SinkEnv;
import engine.sink.SinkFactory;
import feature.compat.ModernProjectiles;
import item.worn.WornStateStore;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import platform.resolve.RegistryResolvers;
import platform.resolve.RuntimeHandles;
import platform.sched.Scheduling;
import testfx.Envs;
import testfx.RecordingSchedulerBackend;
import testfx.Snapshots;

/**
 * The flush-on-hit primary path at the {@link CombatDispatch} seam (ADR-0069): a hit drains ONLY its own
 * attacker's buckets plus the unattributed bucket into the hit's effective-damage fold (one event, one
 * window, one knockback); a third party's bleed is never credited to it. A cancelled flush re-parks through
 * the MONITOR confirm; a same-swing re-hit never drains; no release arms while a combo is active; and a hit
 * after the combo ends kicks the leftover buckets out attributed to their owners.
 */
class ComboDotFlushTest {

    private SinkEnv env;
    private CombatDispatch dispatch;
    private RecordingSchedulerBackend backend;

    @BeforeEach
    void setUp() {
        backend = new RecordingSchedulerBackend();
        Scheduling.install(backend);
        RuntimeHandles handles = new RuntimeHandles(new RegistryResolvers());
        env = Envs.sink().build();
        ModernDispatchSink sink = new ModernDispatchSink(handles, env);
        SinkFactory sinkFactory = mock(SinkFactory.class);
        when(sinkFactory.create(any())).thenReturn(sink);
        ContentHolder content = mock(ContentHolder.class);
        when(content.snapshot()).thenReturn(Snapshots.snapshot().build());
        dispatch = new CombatDispatch(mock(AbilityExecutor.class), sinkFactory, mock(ActorProbe.class), content,
                mock(WornStateStore.class), 0, 1, -1, -1, p -> Optional.empty(), env,
                CombatDispatch.Caps.unlimited(), new ModernProjectiles());
    }

    @AfterEach
    void clearRelays() {
        ParkFlushRelay.clear();
        ReHitGuard.clearSkipped();
    }

    private static Player player() {
        Player p = mock(Player.class);
        when(p.getUniqueId()).thenReturn(UUID.randomUUID());
        when(p.isValid()).thenReturn(true);
        return p;
    }

    private static Player victimPlayer() {
        Player p = player();
        when(p.getNoDamageTicks()).thenReturn(0);       // fresh window (no re-hit skip)
        when(p.getMaximumNoDamageTicks()).thenReturn(20);
        when(p.isDead()).thenReturn(false);
        when(p.getLocation()).thenReturn(mock(Location.class));
        return p;
    }

    private static EntityDamageByEntityEvent hit(Player attacker, Player victim, double damage, double[] committed) {
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getDamager()).thenReturn(attacker);
        when(event.getEntity()).thenReturn(victim);
        when(event.getDamage()).thenReturn(damage);
        when(event.getCause()).thenReturn(EntityDamageEvent.DamageCause.ENTITY_ATTACK);
        if (committed != null) {
            doAnswer(inv -> {
                committed[0] = inv.getArgument(0);
                return null;
            }).when(event).setDamage(anyDouble());
        }
        return event;
    }

    private double drainSum(UUID victim, UUID attacker) {
        return env.dotPark().drainFor(victim, attacker).stream().mapToDouble(DotParkLedger.Bucket::amount).sum();
    }

    @Test
    void parkedDamageJoinsTheHitsFoldForTheMatchingAttacker() {
        Player victim = victimPlayer();
        Player a = player();
        Player b = player();
        DotParkLedger park = env.dotPark();
        park.comboStarted(victim.getUniqueId(), a.getUniqueId(), 0L);
        park.tryPark(victim.getUniqueId(), a, 3.0, 0L);     // bucket[A]
        park.tryPark(victim.getUniqueId(), b, 2.0, 0L);     // bucket[B]
        park.tryPark(victim.getUniqueId(), null, 1.0, 0L);  // UNATTRIBUTED
        double[] committed = {Double.NaN};

        dispatch.onDamage(hit(a, victim, 5.0, committed));

        assertEquals(9.0, committed[0], 1e-9, "base 5.0 + effective (3.0 A + 1.0 unattributed)");
        assertEquals(2.0, drainSum(victim.getUniqueId(), b.getUniqueId()), 1e-9, "the third party's bucket is untouched");
    }

    @Test
    void cancelledFlushReparksThroughTheMonitorConfirm() {
        Player victim = victimPlayer();
        Player a = player();
        DotParkLedger park = env.dotPark();
        park.comboStarted(victim.getUniqueId(), a.getUniqueId(), 0L);
        park.tryPark(victim.getUniqueId(), a, 3.0, 0L);
        park.tryPark(victim.getUniqueId(), null, 1.0, 0L);
        EntityDamageByEntityEvent event = hit(a, victim, 5.0, null);

        dispatch.onDamage(event); // drains A + unattributed, folds, marks the event

        when(event.isCancelled()).thenReturn(true);
        new ComboDotSyncListener(env.dotPark()).onDamageResolved(event);

        assertEquals(4.0, drainSum(victim.getUniqueId(), a.getUniqueId()), 1e-9,
                "the cancelled flush re-parks 3.0 A + 1.0 unattributed");
    }

    @Test
    void reHitSkippedDuplicateNeverDrains() {
        Player victim = victimPlayer();
        when(victim.getNoDamageTicks()).thenReturn(20); // mid-window
        Player a = player();
        env.dotPark().comboStarted(victim.getUniqueId(), a.getUniqueId(), 0L);
        env.dotPark().tryPark(victim.getUniqueId(), a, 3.0, 0L);
        dispatch.reHits().stamp(victim.getUniqueId(), a.getUniqueId(), 0L); // A opened the window

        EntityDamageByEntityEvent event = hit(a, victim, 5.0, null);
        dispatch.onDamage(event);

        assertTrue(ReHitGuard.skipped(event), "the same-swing duplicate is skipped whole");
        assertEquals(3.0, drainSum(victim.getUniqueId(), a.getUniqueId()), 1e-9, "the ledger is untouched by a skipped hit");
    }

    @Test
    void activeComboNeverArmsARelease() {
        Player victim = victimPlayer();
        Player a = player();
        Player b = player();
        env.dotPark().comboStarted(victim.getUniqueId(), a.getUniqueId(), 0L); // combo ACTIVE
        env.dotPark().tryPark(victim.getUniqueId(), b, 2.0, 0L);               // third-party leftover

        dispatch.onDamage(hit(a, victim, 5.0, null));

        assertTrue(backend.repeating.isEmpty(), "no release arms while a combo is active");
        assertEquals(2.0, drainSum(victim.getUniqueId(), b.getUniqueId()), 1e-9, "the third party's bucket stays parked");
    }

    @Test
    void postComboLeftoverBucketsKickTheReleaseOnAHit() {
        Player victim = victimPlayer();
        Player a = player();
        Player b = player();
        env.dotPark().comboStarted(victim.getUniqueId(), a.getUniqueId(), 0L);
        env.dotPark().tryPark(victim.getUniqueId(), b, 2.0, 0L);
        env.dotPark().comboEnded(victim.getUniqueId()); // combo over → !comboActive

        dispatch.onDamage(hit(a, victim, 5.0, null));

        assertEquals(1, backend.repeating.size(), "the post-combo leftover kick arms a release");
        backend.repeating.get(0).task.run(); // window clear → B pays out attributed to B
        verify(victim).damage(2.0, b);
    }
}
