package engine.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
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
        sink.setFlight(player, 100, 0);
        sink.flush();

        verify(player).setAllowFlight(true);
        verify(player).setFlying(true);
        assertEquals(1, backend.delayed.size(), "one delayed revert recorded");
        assertEquals(100L, backend.delayed.get(0).delayTicks(), "recorded with the grant's duration");
    }

    @Test
    void flyRevertOnQuitClearsFlightAndTheLateTimerNoOps() {
        Player player = player();
        sink.setFlight(player, 100, 0);
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
        sink.setFlight(player, 100, 0);
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

    /**
     * A stateful max-health attribute: a FIXED base plus a live modifier list, so {@code getValue()} tracks the
     * drain's negative modifier and its exact removal — the drain no longer writes the base at all (§ADR-0057).
     */
    private static AttributeInstance statefulMaxHealth(double base) {
        AttributeInstance ai = mock(AttributeInstance.class);
        List<AttributeModifier> mods = new ArrayList<>();
        when(ai.getBaseValue()).thenReturn(base);
        when(ai.getValue()).thenAnswer(inv -> base + mods.stream().mapToDouble(AttributeModifier::getAmount).sum());
        doAnswer(inv -> {
            mods.add(inv.getArgument(0));
            return null;
        }).when(ai).addModifier(any(AttributeModifier.class));
        doAnswer(inv -> {
            mods.remove(inv.getArgument(0));
            return null;
        }).when(ai).removeModifier(any(AttributeModifier.class));
        when(ai.getModifiers()).thenAnswer(inv -> new ArrayList<>(mods)); // a copy (like CraftBukkit) — safe to iterate + remove
        return ai;
    }

    @Test
    void maxHealthDrainRemovesTheOverhealthViaAModifierAndRestoresItOnQuit() {
        AttributeInstance ai = statefulMaxHealth(30.0);
        Player player = player();
        when(player.getAttribute(Attribute.GENERIC_MAX_HEALTH)).thenReturn(ai);
        when(player.getHealth()).thenReturn(20.0);

        sink.drainMaxHealth(player, 0.5, 20.0, 0.0, 60); // effective 30, overhealth 10 → -5 modifier → effective 25
        sink.flush();
        assertEquals(25.0, ai.getValue(), 1e-9, "a negative modifier drops the EFFECTIVE cap");
        assertEquals(30.0, ai.getBaseValue(), 1e-9, "the base is never written — drain lives in a modifier");
        assertEquals(1, backend.delayed.size());
        assertEquals(60L, backend.delayed.get(0).delayTicks());

        env.timedReverts().revertAll(uuid); // logout gives the victim their hearts back before the save
        assertEquals(30.0, ai.getValue(), 1e-9, "the drain modifier is removed exactly");

        backend.runDelayed(); // the stranded timer must not double-remove
        assertEquals(30.0, ai.getValue(), 1e-9);
    }

    @Test
    void maxHealthDrainClampsTheModifierSoEffectiveNeverFallsBelowOne() {
        AttributeInstance ai = statefulMaxHealth(4.0);
        Player player = player();
        when(player.getAttribute(Attribute.GENERIC_MAX_HEALTH)).thenReturn(ai);
        when(player.getHealth()).thenReturn(1.0);

        sink.drainMaxHealth(player, 0.0, 20.0, 100.0, 60); // flat 100 clamps to effective-1=3 → effective floors at 1
        sink.flush();
        assertEquals(1.0, ai.getValue(), 1e-9, "the modifier magnitude clamps so effective floors at 1");

        env.timedReverts().revertAll(uuid);
        assertEquals(4.0, ai.getValue(), 1e-9, "removing the modifier restores the true original");
    }

    @Test
    void maxHealthZeroDrainRegistersNothing() {
        AttributeInstance ai = statefulMaxHealth(20.0);
        Player player = player();
        when(player.getAttribute(Attribute.GENERIC_MAX_HEALTH)).thenReturn(ai);
        when(player.getHealth()).thenReturn(20.0);

        sink.drainMaxHealth(player, 0.5, 20.0, 0.0, 60); // no overhealth over baseline → early return
        sink.flush();

        verify(ai, never()).addModifier(any(AttributeModifier.class));
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
        assertEquals(25.0, ai.getValue(), 1e-9);
        assertTrue(env.timedReverts().isEmpty(), "a mob is never registered in the quit registry");

        backend.runDelayed(); // the direct scheduled restore still fires
        assertEquals(30.0, ai.getValue(), 1e-9);
    }
}
