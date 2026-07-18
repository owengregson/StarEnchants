package feature.reforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
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
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
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
 * The Singularity collapsing star (ADR-0071): the reforge service raycasts the sighted block, beams to it,
 * pulses every living thing toward the core, then implodes with linear falloff floored at {@code falloff-floor}.
 * Wells are location-anchored (an owner quit drops only attribution). Core at a round point so the pull/implode
 * geometry is hand-checkable; the repeating body is driven tick-by-tick through {@link RecordingSchedulerBackend}.
 */
class GravityWellServiceTest {

    private static final UUID ACTOR_ID = UUID.randomUUID();

    private RecordingSchedulerBackend backend;
    private final World world = mock(World.class);
    private final TriggerDispatch dispatch = mock(TriggerDispatch.class);
    private final Messages messages = mock(Messages.class);

    @BeforeEach
    void setUp() {
        backend = new RecordingSchedulerBackend();
        Scheduling.install(backend);
        Regions.install(false);
        GravityWellService.clearAll();
    }

    @AfterEach
    void tearDown() {
        GravityWellService.clearAll();
        Regions.install(Capabilities.foliaPresent());
    }

    private GravityWellService service(BiFunction<Player, Integer, Block> targetBlock) {
        return new GravityWellService(dispatch, targetBlock, messages, PlatformResolvers.none());
    }

    private Player actor() {
        Player p = mock(Player.class);
        when(p.getUniqueId()).thenReturn(ACTOR_ID);
        when(p.isValid()).thenReturn(true);
        when(p.getEyeLocation()).thenReturn(new Location(world, 0.5, 66, 0.5));
        return p;
    }

    private Block blockAtOrigin() {
        Block block = mock(Block.class);
        when(block.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        return block;
    }

    private LivingEntity livingAt(UUID id, Location loc) {
        LivingEntity e = mock(LivingEntity.class);
        when(e.getUniqueId()).thenReturn(id);
        when(e.isValid()).thenReturn(true);
        when(e.getLocation()).thenReturn(loc);
        when(e.getVelocity()).thenReturn(new Vector(0, 0, 0));
        return e;
    }

    private void stageNearby(List<Entity> found) {
        when(world.getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble())).thenReturn(found);
    }

    /** rise 0 → the block at (0,64,0) yields core (0.5, 65, 0.5). */
    private static Location core() {
        return new Location(null, 0.5, 65, 0.5);
    }

    private static CompiledEffect wellFx(int duration, int period, boolean selfPull, boolean selfDamage) {
        Args args = Args.empty()
                .with("range", 12.0).with("radius", 6.0).with("rise", 0.0)
                .with("duration", duration).with("period", period)
                .with("pull", 0.28).with("damage", 8.0).with("falloff-floor", 0.25)
                .with("self-pull", selfPull).with("self-damage", selfDamage)
                .with("r", 190).with("g", 120).with("b", 255);
        return new CompiledEffect("GRAVITY_WELL", args, null, 0, Affinity.CONTEXT_LOCAL);
    }

    private void tick() {
        backend.repeating.get(0).task.run();
    }

    @Test
    void noTargetBlockSendsFragmentAndArmsNothing() {
        service((p, d) -> null).start(actor(), wellFx(60, 2, true, true));

        verify(messages).fragment(eq("reforge.singularity.no-target"), eq("RANGE"), anyInt());
        assertEquals(0, GravityWellService.liveCount());
        assertTrue(backend.repeating.isEmpty(), "no repeating task is armed when the raycast misses");
    }

    @Test
    void pullPulseAddsVelocityTowardCore() {
        LivingEntity west = livingAt(UUID.randomUUID(), new Location(world, 2.5, 65, 0.5));   // +x of core, dist 2
        LivingEntity north = livingAt(UUID.randomUUID(), new Location(world, 0.5, 65, -3.5)); // -z of core, dist 4
        stageNearby(List.of(west, north));

        service((p, d) -> blockAtOrigin()).start(actor(), wellFx(60, 2, true, true));
        tick(); // elapsed = 2 < 60 → a pull pulse, no implosion

        ArgumentCaptor<Vector> vWest = ArgumentCaptor.forClass(Vector.class);
        verify(west).setVelocity(vWest.capture());
        assertEquals(-0.28, vWest.getValue().getX(), 1.0e-9); // (core-victim)/dist × pull, +x source pulled -x
        assertEquals(0.04, vWest.getValue().getY(), 1.0e-9);  // the +y anti-gravity term
        assertEquals(0.0, vWest.getValue().getZ(), 1.0e-9);

        ArgumentCaptor<Vector> vNorth = ArgumentCaptor.forClass(Vector.class);
        verify(north).setVelocity(vNorth.capture());
        assertEquals(0.0, vNorth.getValue().getX(), 1.0e-9);
        assertEquals(0.04, vNorth.getValue().getY(), 1.0e-9);
        assertEquals(0.28, vNorth.getValue().getZ(), 1.0e-9); // -z source pulled +z
    }

    @Test
    void selfPullFalseSparesTheCaster() {
        LivingEntity casterBody = livingAt(ACTOR_ID, new Location(world, 2.5, 65, 0.5));
        LivingEntity other = livingAt(UUID.randomUUID(), new Location(world, 0.5, 65, -3.5));
        stageNearby(List.of(casterBody, other));

        service((p, d) -> blockAtOrigin()).start(actor(), wellFx(60, 2, false, true));
        tick();

        verify(casterBody, never()).setVelocity(any(Vector.class)); // self-pull off spares the caster
        verify(other).setVelocity(any(Vector.class));
    }

    @Test
    void selfDamageFalseSparesTheCaster() {
        LivingEntity casterBody = livingAt(ACTOR_ID, core().clone());
        LivingEntity other = livingAt(UUID.randomUUID(), new Location(world, 3.5, 65, 0.5));
        stageNearby(List.of(casterBody, other));

        service((p, d) -> blockAtOrigin()).start(actor(), wellFx(2, 2, true, false));
        tick(); // elapsed = 2 >= 2 → implosion

        verify(casterBody, never()).damage(anyDouble(), any());
        verify(casterBody, never()).damage(anyDouble());
        verify(other).damage(eq(4.0), any(LivingEntity.class)); // dist 3, radius 6 → 8 × 0.5
    }

    @Test
    void implodeDamageFallsOffLinearlyAndFloors() {
        LivingEntity centre = livingAt(UUID.randomUUID(), core().clone().add(0, 0, 0)); // dist 0
        LivingEntity mid = livingAt(UUID.randomUUID(), new Location(world, 3.5, 65, 0.5)); // dist 3
        LivingEntity edge = livingAt(UUID.randomUUID(), new Location(world, 0.5, 65, 6.4)); // dist 5.9
        stageNearby(List.of(centre, mid, edge));
        Player caster = actor();

        service((p, d) -> blockAtOrigin()).start(caster, wellFx(2, 2, true, true));
        tick();

        verify(centre).damage(8.0, caster);   // full at the core
        verify(mid).damage(4.0, caster);      // 8 × (1 − 3/6)
        verify(edge).damage(2.0, caster);     // 8 × floor 0.25
    }

    @Test
    void implodeDegradesToBareDamageAfterOwnerQuit() {
        LivingEntity centre = livingAt(UUID.randomUUID(), core().clone());
        stageNearby(List.of(centre));

        service((p, d) -> blockAtOrigin()).start(actor(), wellFx(2, 2, true, true));
        GravityWellService.scope().clear(ACTOR_ID); // the caster quits → attribution handle dropped, the star lives
        tick();

        verify(centre).damage(8.0);                 // bare, unattributed
        verify(centre, never()).damage(anyDouble(), any());
    }

    @Test
    void implodeAndTeardownShareTheFinalRun() {
        LivingEntity victim = livingAt(UUID.randomUUID(), core().clone());
        stageNearby(List.of(victim));

        service((p, d) -> blockAtOrigin()).start(actor(), wellFx(2, 2, true, true));
        tick(); // implode + drop in one run

        assertEquals(0, GravityWellService.liveCount());
        assertTrue(backend.repeating.get(0).isCancelled(), "the well task is cancelled at teardown");
        tick(); // a stray run after teardown must not re-implode
        verify(victim, times(1)).damage(anyDouble(), any());
    }

    @Test
    void clearAllCancelsEveryWell() {
        stageNearby(List.of());
        service((p, d) -> blockAtOrigin()).start(actor(), wellFx(60, 2, true, true));
        Player second = mock(Player.class);
        when(second.getUniqueId()).thenReturn(UUID.randomUUID());
        when(second.getEyeLocation()).thenReturn(new Location(world, 10, 66, 10));
        service((p, d) -> blockAtOrigin()).start(second, wellFx(60, 2, true, true));

        assertEquals(2, GravityWellService.liveCount());
        GravityWellService.clearAll();

        assertEquals(0, GravityWellService.liveCount());
        assertTrue(backend.repeating.get(0).isCancelled());
        assertTrue(backend.repeating.get(1).isCancelled());
    }
}
