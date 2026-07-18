package feature.reforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import compile.model.Affinity;
import compile.model.CompiledEffect;
import compile.resolve.PlatformResolvers;
import feature.trigger.TriggerDispatch;
import java.util.UUID;
import java.util.function.BiFunction;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import platform.caps.Capabilities;
import platform.caps.Regions;
import platform.lang.Messages;
import platform.sched.Scheduling;
import schema.spec.Args;
import testfx.RecordingSchedulerBackend;

/**
 * The Castling channel (ADR-0071): acquire the crosshair enemy, channel with an audible per-second countdown,
 * then swap positions — velocities zeroed, each keeps their OWN facing. LOS/range/world/liveness breaks abort;
 * the cooldown is never refunded (the service holds no {@code CooldownStore}). The caster-scheduler task is
 * driven tick-by-tick through {@link RecordingSchedulerBackend}; the victim/caster hops run inline.
 */
class CastlingServiceTest {

    private static final UUID CASTER_ID = UUID.randomUUID();

    private RecordingSchedulerBackend backend;
    private final World world = mock(World.class);
    private final TriggerDispatch dispatch = mock(TriggerDispatch.class);
    private final Messages messages = mock(Messages.class);

    @BeforeEach
    void setUp() {
        backend = new RecordingSchedulerBackend();
        Scheduling.install(backend);
        Regions.install(false);
        CastlingService.clearAll();
    }

    @AfterEach
    void tearDown() {
        CastlingService.clearAll();
        Regions.install(Capabilities.foliaPresent());
    }

    private CastlingService service(BiFunction<Player, Integer, Entity> targetEntity) {
        return new CastlingService(dispatch, targetEntity, messages, PlatformResolvers.none());
    }

    private Player caster(Location at) {
        Player p = mock(Player.class);
        when(p.getUniqueId()).thenReturn(CASTER_ID);
        when(p.isValid()).thenReturn(true);
        when(p.isDead()).thenReturn(false);
        when(p.getWorld()).thenReturn(world);
        when(p.getLocation()).thenReturn(at);
        when(p.getName()).thenReturn("Caster");
        return p;
    }

    private Player victim(Location at) {
        Player v = mock(Player.class);
        when(v.getUniqueId()).thenReturn(UUID.randomUUID());
        when(v.isValid()).thenReturn(true);
        when(v.isDead()).thenReturn(false);
        when(v.getWorld()).thenReturn(world);
        when(v.getLocation()).thenReturn(at);
        when(v.getName()).thenReturn("Victim");
        return v;
    }

    private static CompiledEffect castlingFx() {
        Args args = Args.empty()
                .with("range", 12.0).with("channel", 40).with("check-period", 2)
                .with("cue-period", 10).with("range-slack", 2.0);
        return new CompiledEffect("SWAP_POSITION", args, null, 0, Affinity.CONTEXT_LOCAL);
    }

    private void driveTicks(int count) {
        for (int i = 0; i < count; i++) {
            backend.repeating.get(0).task.run();
        }
    }

    @Test
    void noCrosshairTargetFailsFeedbackOnly() {
        service((p, d) -> null).start(caster(new Location(world, 0, 64, 0)), castlingFx());

        verify(messages).fragment(eq("reforge.castling.no-target"), eq("RANGE"), anyInt());
        assertFalse(CastlingService.channelActive(CASTER_ID));
        assertEquals(0, backend.repeating.size(), "no channel task is armed on a whiffed acquisition");
    }

    @Test
    void selfHitRejected() {
        Player actor = caster(new Location(world, 0, 64, 0));
        service((p, d) -> actor).start(actor, castlingFx()); // the ray returns the caster itself

        verify(messages).fragment(eq("reforge.castling.no-target"), eq("RANGE"), anyInt());
        assertFalse(CastlingService.channelActive(CASTER_ID));
    }

    @Test
    void losBreakAborts() {
        Player actor = caster(new Location(world, 0, 64, 0));
        Player target = victim(new Location(world, 8, 64, 0));
        when(actor.hasLineOfSight(target)).thenReturn(false); // sight lost mid-channel
        service((p, d) -> target).start(actor, castlingFx());

        driveTicks(1);

        assertFalse(CastlingService.channelActive(CASTER_ID));
        verify(dispatch, never()).teleportEntity(any(), any());
        verify(messages).fragment(eq("reforge.castling.aborted"));
    }

    @Test
    void rangePlusSlackAborts() {
        Player actor = caster(new Location(world, 0, 64, 0));
        Player target = victim(new Location(world, 30, 64, 0)); // 30 > range 12 + slack 2
        when(actor.hasLineOfSight(target)).thenReturn(true);
        service((p, d) -> target).start(actor, castlingFx());

        driveTicks(1);

        assertFalse(CastlingService.channelActive(CASTER_ID));
        verify(dispatch, never()).teleportEntity(any(), any());
    }

    @Test
    void victimInvalidAborts() {
        Player actor = caster(new Location(world, 0, 64, 0));
        Player target = victim(new Location(world, 8, 64, 0));
        when(actor.hasLineOfSight(target)).thenReturn(true);
        service((p, d) -> target).start(actor, castlingFx());
        when(target.isValid()).thenReturn(false); // the victim died/left after acquisition

        driveTicks(1);

        assertFalse(CastlingService.channelActive(CASTER_ID));
        verify(dispatch, never()).teleportEntity(any(), any());
    }

    @Test
    void worldChangeAborts() {
        Player actor = caster(new Location(world, 0, 64, 0));
        Player target = victim(new Location(world, 8, 64, 0));
        when(actor.hasLineOfSight(target)).thenReturn(true);
        service((p, d) -> target).start(actor, castlingFx());
        when(target.getWorld()).thenReturn(mock(World.class)); // the victim crossed worlds

        driveTicks(1);

        assertFalse(CastlingService.channelActive(CASTER_ID));
        verify(dispatch, never()).teleportEntity(any(), any());
    }

    @Test
    void countdownCuesFireOncePerSecond() {
        Player actor = caster(new Location(world, 0, 64, 0));
        Player target = victim(new Location(world, 8, 64, 0));
        when(actor.hasLineOfSight(target)).thenReturn(true);
        service((p, d) -> target).start(actor, castlingFx());

        driveTicks(20); // 40t channel at check-period 2 → completion

        // Exactly one countdown per second-change: at start (2 left) and at 20t elapsed (1 left); the 0 = swap, no cue.
        verify(messages, times(2)).fragment(eq("reforge.castling.countdown"), eq("SECONDS"), anyInt());
    }

    @Test
    void completedChannelSwapsWithFacingsKept() {
        Location casterLoc = new Location(world, 0, 64, 0, 10f, 5f);
        Location victimLoc = new Location(world, 8, 64, 0, 170f, -3f);
        Player actor = caster(casterLoc);
        Player target = victim(victimLoc);
        when(actor.hasLineOfSight(target)).thenReturn(true);
        service((p, d) -> target).start(actor, castlingFx());

        driveTicks(20);

        ArgumentCaptor<Location> victimDest = ArgumentCaptor.forClass(Location.class);
        verify(dispatch).teleportEntity(eq(target), victimDest.capture());
        assertEquals(0, victimDest.getValue().getX(), 1.0e-9);   // to the caster's spot
        assertEquals(170f, victimDest.getValue().getYaw());       // keeping the VICTIM's own facing
        assertEquals(-3f, victimDest.getValue().getPitch());

        ArgumentCaptor<Location> casterDest = ArgumentCaptor.forClass(Location.class);
        verify(dispatch).teleportEntity(eq(actor), casterDest.capture());
        assertEquals(8, casterDest.getValue().getX(), 1.0e-9);    // to the victim's spot
        assertEquals(10f, casterDest.getValue().getYaw());        // keeping the CASTER's own facing
        assertEquals(5f, casterDest.getValue().getPitch());

        verify(target).setVelocity(new Vector(0, 0, 0));
        verify(actor).setVelocity(new Vector(0, 0, 0));
        assertFalse(CastlingService.channelActive(CASTER_ID));
    }

    @Test
    void casterQuitSweepCancelsTheChannel() {
        Player actor = caster(new Location(world, 0, 64, 0));
        Player target = victim(new Location(world, 8, 64, 0));
        when(actor.hasLineOfSight(target)).thenReturn(true);
        service((p, d) -> target).start(actor, castlingFx());

        CastlingService.scope().clear(CASTER_ID); // the caster quits
        driveTicks(1); // the surviving task sees no channel and no-ops

        assertFalse(CastlingService.channelActive(CASTER_ID));
        verify(dispatch, never()).teleportEntity(any(), any());
    }

    @Test
    void abortNeverRefunds() {
        // Structural, compile-time proof: the 4-arg constructor takes NO CooldownStore, so an abort cannot release
        // a spent cooldown — there is simply no collaborator to call. An aborted channel just drops.
        Player actor = caster(new Location(world, 0, 64, 0));
        Player target = victim(new Location(world, 8, 64, 0));
        when(actor.hasLineOfSight(target)).thenReturn(false);
        service((p, d) -> target).start(actor, castlingFx());

        driveTicks(1);

        assertFalse(CastlingService.channelActive(CASTER_ID));
    }
}
