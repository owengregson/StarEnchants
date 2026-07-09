package engine.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import platform.resolve.RegistryResolvers;
import platform.resolve.RuntimeHandles;
import platform.sched.Scheduling;
import testfx.Envs;
import testfx.RecordingSchedulerBackend;

/**
 * The logout-permanence closure for timed buffs (F07/F08): a timed grant applies inline on flush and records
 * exactly one delayed revert; a mid-window quit ({@code timedReverts().revertAll}) performs the revert while the
 * later expiry timer no-ops (pinning both the logout fix and the Paper stale-offline-player fix); and a
 * non-player target keeps the direct scheduled revert with no registry entry. The
 * {@link RecordingSchedulerBackend} captures the delayed hops so the quit-vs-timer ordering is observable.
 */
class DispatchSinkTimedRevertTest {

    private RuntimeHandles handles;
    private RecordingSchedulerBackend backend;
    private SinkEnv env;
    private ModernDispatchSink sink;
    private final UUID uuid = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        handles = new RuntimeHandles(new RegistryResolvers());
        backend = new RecordingSchedulerBackend();
        Scheduling.install(backend);
        env = Envs.sink().build();
        sink = new ModernDispatchSink(handles, env);
    }

    private Player player() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL); // clearTemporaryFlight only clears survival/adventure
        return player;
    }

    // ── F08: FLY ──────────────────────────────────────────────────────────────────────────────────

    @Test
    void flyGrantAppliesInlineAndRecordsOneRevert() {
        Player player = player();
        sink.setFlight(player, 100);
        sink.flush();

        verify(player).setAllowFlight(true);
        verify(player).setFlying(true);
        assertEquals(1, backend.delayed.size(), "one delayed revert recorded");
        assertEquals(100L, backend.delayed.get(0).delayTicks(), "recorded with the grant's duration");
    }

    @Test
    void flyRevertOnQuitClearsFlightAndTheLateTimerNoOps() {
        Player player = player();
        sink.setFlight(player, 100);
        sink.flush();

        env.timedReverts().revertAll(uuid); // logout mid-window
        verify(player).setFlying(false);
        verify(player).setAllowFlight(false);

        backend.runDelayed(); // the stranded timer fires against the (now-offline) player — must no-op
        verify(player, times(1)).setFlying(false);
        verify(player, times(1)).setAllowFlight(false);
        assertTrue(env.timedReverts().isEmpty(), "the drain emptied the registry");
    }

    @Test
    void flyNormalExpiryClearsFlightAndAQuitAfterwardsNoOps() {
        Player player = player();
        sink.setFlight(player, 100);
        sink.flush();

        backend.runDelayed(); // normal expiry
        verify(player).setFlying(false);

        env.timedReverts().revertAll(uuid); // a later quit finds nothing outstanding
        verify(player, times(1)).setFlying(false);
    }

    // ── F08: MOVEMENT_SPEED ─────────────────────────────────────────────────────────────────────────

    @Test
    void movementSpeedGrantAppliesInlineAndRevertsToVanillaDefault() {
        Player player = player();
        sink.movementSpeed(player, 0.5, 60);
        sink.flush();

        verify(player).setWalkSpeed(0.5f);
        assertEquals(1, backend.delayed.size());
        assertEquals(60L, backend.delayed.get(0).delayTicks());

        env.timedReverts().revertAll(uuid);
        verify(player).setWalkSpeed(0.2f);

        backend.runDelayed();
        verify(player, times(1)).setWalkSpeed(0.2f); // the late timer does not re-write speed
    }

    // ── F08: INVINCIBLE ──────────────────────────────────────────────────────────────────────────────

    @Test
    void invincibleGrantAppliesInlineAndRevertsOnQuit() {
        Player player = player();
        sink.invincible(player, 40);
        sink.flush();

        verify(player).setInvulnerable(true);
        assertEquals(1, backend.delayed.size());
        assertEquals(40L, backend.delayed.get(0).delayTicks());

        env.timedReverts().revertAll(uuid);
        verify(player).setInvulnerable(false);

        backend.runDelayed();
        verify(player, times(1)).setInvulnerable(false);
    }

    @Test
    void invincibleOnANonPlayerKeepsTheDirectScheduledRevert() {
        LivingEntity mob = mock(LivingEntity.class); // never a Player — mobs cannot quit
        sink.invincible(mob, 40);
        sink.flush();

        verify(mob).setInvulnerable(true);
        assertEquals(1, backend.delayed.size());
        assertTrue(env.timedReverts().isEmpty(), "a mob is never registered in the quit registry");

        backend.runDelayed(); // the direct scheduled revert still fires
        verify(mob).setInvulnerable(false);
    }

    // ── F07: max-health drain ────────────────────────────────────────────────────────────────────────

    /** A stateful max-health attribute whose {@code baseValue} tracks writes, so exact-delta restore is testable. */
    private static AttributeInstance statefulMaxHealth(double initialBase) {
        AttributeInstance ai = mock(AttributeInstance.class);
        AtomicReference<Double> base = new AtomicReference<>(initialBase);
        when(ai.getBaseValue()).thenAnswer(inv -> base.get());
        when(ai.getValue()).thenAnswer(inv -> base.get());
        doAnswer(inv -> {
            base.set(inv.getArgument(0));
            return null;
        }).when(ai).setBaseValue(anyDouble());
        return ai;
    }

    @Test
    void maxHealthDrainRestoresTheExactDeltaOnQuitAndTheLateTimerNoOps() {
        AttributeInstance ai = statefulMaxHealth(30.0);
        Player player = player();
        when(player.getAttribute(Attribute.GENERIC_MAX_HEALTH)).thenReturn(ai);
        when(player.getHealth()).thenReturn(20.0);

        sink.drainMaxHealth(player, 0.5, 20.0, 0.0, 60); // overhealth 10 → drain 5 → base 25, removed 5
        sink.flush();
        assertEquals(25.0, ai.getBaseValue(), 1e-9, "drain applied inline");
        assertEquals(1, backend.delayed.size());
        assertEquals(60L, backend.delayed.get(0).delayTicks());

        env.timedReverts().revertAll(uuid); // logout gives the victim their hearts back before the save
        assertEquals(30.0, ai.getBaseValue(), 1e-9, "exact removed delta restored");

        backend.runDelayed(); // the stranded timer must not add the delta a second time
        assertEquals(30.0, ai.getBaseValue(), 1e-9);
    }

    @Test
    void maxHealthDrainExactDeltaSurvivesTheFloorClamp() {
        AttributeInstance ai = statefulMaxHealth(4.0);
        Player player = player();
        when(player.getAttribute(Attribute.GENERIC_MAX_HEALTH)).thenReturn(ai);
        when(player.getHealth()).thenReturn(1.0);

        sink.drainMaxHealth(player, 0.0, 20.0, 100.0, 60); // flat 100 → newBase clamps to 1, removed = 3
        sink.flush();
        assertEquals(1.0, ai.getBaseValue(), 1e-9, "base clamps at the 1.0 floor");

        env.timedReverts().revertAll(uuid);
        assertEquals(4.0, ai.getBaseValue(), 1e-9, "the clamped removed delta restores the true original");
    }

    @Test
    void maxHealthZeroDrainRegistersNothing() {
        AttributeInstance ai = statefulMaxHealth(20.0);
        Player player = player();
        when(player.getAttribute(Attribute.GENERIC_MAX_HEALTH)).thenReturn(ai);
        when(player.getHealth()).thenReturn(20.0);

        sink.drainMaxHealth(player, 0.5, 20.0, 0.0, 60); // no overhealth over baseline → early return
        sink.flush();

        verify(ai, never()).setBaseValue(anyDouble());
        assertTrue(backend.delayed.isEmpty(), "no revert scheduled for a zero drain");
        assertTrue(env.timedReverts().isEmpty(), "the registry never accumulates an empty grant");
    }

    @Test
    void maxHealthDrainOnANonPlayerKeepsTheDirectScheduledRestore() {
        AttributeInstance ai = statefulMaxHealth(30.0);
        LivingEntity mob = mock(LivingEntity.class);
        when(mob.getAttribute(Attribute.GENERIC_MAX_HEALTH)).thenReturn(ai);
        when(mob.getHealth()).thenReturn(20.0);

        sink.drainMaxHealth(mob, 0.5, 20.0, 0.0, 60);
        sink.flush();
        assertEquals(25.0, ai.getBaseValue(), 1e-9);
        assertTrue(env.timedReverts().isEmpty(), "a mob is never registered in the quit registry");

        backend.runDelayed(); // the direct scheduled restore still fires
        assertEquals(30.0, ai.getBaseValue(), 1e-9);
    }
}
