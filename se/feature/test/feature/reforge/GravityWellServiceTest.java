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
import org.bukkit.Material;
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
import platform.sched.Scheduling;
import schema.spec.Args;
import testfx.RecordingSchedulerBackend;

/**
 * The Singularity collapsing star (ADR-0071): the reforge service raycasts the sighted block, beams to it,
 * pulses every living thing within the radius SPHERE toward the core, then implodes with linear falloff floored
 * at {@code falloff-floor}. An air aim (no block, or a faulted Folia raycast) DROPS the core to the ground
 * beneath the ray's end; only a bottomless drop keeps a mid-air core at eye + direction × range. Wells are
 * location-anchored (an owner quit drops only
 * attribution). Core at a round point so the pull/implode geometry is hand-checkable; the repeating body is
 * driven tick-by-tick through {@link RecordingSchedulerBackend}.
 */
class GravityWellServiceTest {

    private static final UUID ACTOR_ID = UUID.randomUUID();

    private RecordingSchedulerBackend backend;
    private final World world = mock(World.class);
    private final TriggerDispatch dispatch = mock(TriggerDispatch.class);

    @BeforeEach
    void setUp() {
        backend = new RecordingSchedulerBackend();
        Scheduling.install(backend);
        Regions.install(false);
        GravityWellService.clearAll();
        // Default the world to open AIR so an air aim's ground-drop scan finds nothing → the mid-air fallback is
        // exercised legitimately (not by a swallowed mock NPE). The ground-drop test overrides one column cell.
        Block air = mock(Block.class);
        when(air.getType()).thenReturn(Material.AIR);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(air);
    }

    @AfterEach
    void tearDown() {
        GravityWellService.clearAll();
        Regions.install(Capabilities.foliaPresent());
    }

    private GravityWellService service(BiFunction<Player, Integer, Block> targetBlock) {
        return new GravityWellService(dispatch, targetBlock, PlatformResolvers.none());
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
    void bottomlessAirAimKeepsTheMidAirCore() {
        // Eye (0.5, 66, 0.5) yaw 0 → +Z; range 12, no ground anywhere below (all-air world) → the core stays at
        // the ray's end, mid-air, (0.5, 66, 12.5).
        LivingEntity shy = livingAt(UUID.randomUUID(), new Location(world, 0.5, 66, 10.5)); // 2 short of the core
        stageNearby(List.of(shy));

        service((p, d) -> null).start(actor(), wellFx(60, 2, true, true));

        assertEquals(1, GravityWellService.liveCount());
        tick();
        ArgumentCaptor<Vector> v = ArgumentCaptor.forClass(Vector.class);
        verify(shy).setVelocity(v.capture());
        assertEquals(0.0, v.getValue().getX(), 1.0e-9);
        assertEquals(0.04, v.getValue().getY(), 1.0e-9);
        assertEquals(0.28, v.getValue().getZ(), 1.0e-9); // pulled +Z toward the mid-air core
    }

    @Test
    void airAimDropsToGroundBeneathTheRayEnd() {
        // Eye (0.5,66,0.5) yaw 0 → +Z; range 12 → the ray ends in air at (0.5,66,12.5). A solid block at (0,60,12)
        // beneath it → the well DROPS to the ground core (0.5, 60+1+rise=61, 12.5), NOT the mid-air point.
        Block ground = mock(Block.class);
        when(ground.getType()).thenReturn(Material.STONE);
        when(ground.getLocation()).thenReturn(new Location(world, 0, 60, 12));
        when(world.getBlockAt(0, 60, 12)).thenReturn(ground); // the first solid straight down the ray-end column

        LivingEntity above = livingAt(UUID.randomUUID(), new Location(world, 0.5, 65, 12.5)); // 4 ABOVE the ground core
        stageNearby(List.of(above));

        service((p, d) -> null).start(actor(), wellFx(60, 2, true, true));

        assertEquals(1, GravityWellService.liveCount());
        tick();
        ArgumentCaptor<Vector> v = ArgumentCaptor.forClass(Vector.class);
        verify(above).setVelocity(v.capture());
        assertEquals(0.0, v.getValue().getX(), 1.0e-9);
        assertEquals(0.0, v.getValue().getZ(), 1.0e-9);
        assertTrue(v.getValue().getY() < 0, "pulled DOWN toward the ground-dropped core, not up to a mid-air one");
    }

    @Test
    void faultedSightRaycastFallsIntoTheAirAnchor() {
        stageNearby(List.of());
        service((p, d) -> {
            throw new IllegalStateException("cross-region read"); // the Folia unowned-region fault
        }).start(actor(), wellFx(60, 2, true, true));

        assertEquals(1, GravityWellService.liveCount());
    }

    @Test
    void cubeCornerOutsideTheSphereIsSpared() {
        // (5.5, 65, 4.5) vs core (0.5, 65, 0.5): dx 5, dz 4 → dist √41 ≈ 6.4 — inside the r=6 CUBE that
        // getNearbyEntities scans, outside the authored sphere.
        LivingEntity corner = livingAt(UUID.randomUUID(), new Location(world, 5.5, 65, 4.5));
        LivingEntity inside = livingAt(UUID.randomUUID(), new Location(world, 3.5, 65, 0.5));
        stageNearby(List.of(corner, inside));

        service((p, d) -> blockAtOrigin()).start(actor(), wellFx(60, 2, true, true));
        tick(); // a pull pulse

        verify(corner, never()).setVelocity(any(Vector.class));
        verify(inside).setVelocity(any(Vector.class));
    }

    @Test
    void implosionTrimsTheCubeCornerToTheSphere() {
        LivingEntity corner = livingAt(UUID.randomUUID(), new Location(world, 5.5, 65, 4.5)); // dist √41 > 6
        LivingEntity inside = livingAt(UUID.randomUUID(), new Location(world, 3.5, 65, 0.5)); // dist 3
        stageNearby(List.of(corner, inside));

        service((p, d) -> blockAtOrigin()).start(actor(), wellFx(2, 2, true, true));
        tick(); // elapsed 2 >= 2 → implosion

        verify(corner, never()).damage(anyDouble(), any());
        verify(corner, never()).damage(anyDouble());
        verify(inside).damage(eq(4.0), any(LivingEntity.class)); // 8 × (1 − 3/6)
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
