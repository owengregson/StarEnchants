package feature.reforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import compile.model.Affinity;
import compile.model.CompiledEffect;
import compile.resolve.PlatformResolvers;
import feature.trigger.TriggerDispatch;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
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
 * The Javelin flight + control-lock (ADR-0071): a straight particle javelin along the FULL facing (pitch
 * included), priced AT THROW; the first living hit takes the swing, knockback back along the flight angle, a
 * per-tick control-lock for {@code lock} ticks (the view is snapped back only on drift, movement is slowed —
 * position is NEVER pinned, so the victim falls the knockback arc), then — only after the lock releases —
 * Nausea. A wall or the budget ends the flight. The self-rescheduling chain and the lock are driven through
 * {@link RecordingSchedulerBackend}: immediate hops run inline, {@code *Later} hops are replayed by delay.
 */
class JavelinServiceTest {

    private static final UUID THROWER_ID = UUID.randomUUID();

    private RecordingSchedulerBackend backend;
    private final World world = mock(World.class);
    private final TriggerDispatch dispatch = mock(TriggerDispatch.class);
    private final WeaponDamage weaponDamage = mock(WeaponDamage.class);
    private Block airBlock;

    @BeforeEach
    void setUp() {
        backend = new RecordingSchedulerBackend();
        Scheduling.install(backend);
        Regions.install(false);
        JavelinService.clearAll();
        ControlLockService.clearAll(); // the hold moved to its own service — clear it too
        airBlock = mock(Block.class);
        when(airBlock.getType()).thenReturn(Material.AIR);
        allAir();
    }

    @AfterEach
    void tearDown() {
        JavelinService.clearAll();
        ControlLockService.clearAll();
        Regions.install(Capabilities.foliaPresent());
    }

    private JavelinService service() {
        return service(() -> true, () -> true, (thrower, victim) -> false);
    }

    private JavelinService service(BooleanSupplier pvp, BooleanSupplier pve, BiPredicate<Player, Player> friendly) {
        return new JavelinService(dispatch, weaponDamage, PlatformResolvers.none(), pvp, pve, friendly);
    }

    /** All blocks passable unless a scenario overrides {@code getBlockAt}. */
    private void allAir() {
        when(world.getBlockAt(any(Location.class))).thenReturn(airBlock);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(airBlock);
    }

    private Player thrower(float yaw, float pitch) {
        Player p = mock(Player.class);
        when(p.getUniqueId()).thenReturn(THROWER_ID);
        when(p.isValid()).thenReturn(true);
        when(p.getEyeLocation()).thenReturn(new Location(world, 0.5, 65, 0.5, yaw, pitch));
        return p;
    }

    private LivingEntity cow(Location at) {
        LivingEntity e = mock(LivingEntity.class);
        when(e.getUniqueId()).thenReturn(UUID.randomUUID());
        when(e.isValid()).thenReturn(true);
        when(e.getLocation()).thenReturn(at);
        when(e.getVelocity()).thenReturn(new Vector(0, 0, 0));
        return e;
    }

    private Player playerVictim(Location at) {
        Player p = mock(Player.class);
        when(p.getUniqueId()).thenReturn(UUID.randomUUID());
        when(p.isValid()).thenReturn(true);
        when(p.getLocation()).thenReturn(at);
        when(p.getVelocity()).thenReturn(new Vector(0, 0, 0));
        return p;
    }

    private void stageNearby(List<Entity> found) {
        when(world.getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble())).thenReturn(found);
    }

    private static CompiledEffect javelinFx(String mode, double flatDamage) {
        Args args = Args.empty()
                .with("speed", 0.15).with("max-travel", 12.0).with("hit-radius", 0.9)
                .with("damage-mode", mode).with("damage", flatDamage)
                .with("knockback", 1.3).with("knockback-base", 0.45)
                .with("lock", 20).with("lock-delay", 5)
                .with("nausea-effect", 9).with("nausea-duration", 100)
                .with("particle", 7).with("r", 120).with("g", 200).with("b", 255).with("size", 1.2);
        return new CompiledEffect("JAVELIN", args, null, 0, Affinity.CONTEXT_LOCAL);
    }

    /** Run every recorded delayed hop whose delay is {@code delay} (advances the flight or fires an armed hook). */
    private void runDue(long delay) {
        List<RecordingSchedulerBackend.Delayed> due = new ArrayList<>();
        for (RecordingSchedulerBackend.Delayed d : backend.delayed) {
            if (d.delayTicks() == delay) {
                due.add(d);
            }
        }
        backend.delayed.removeAll(due);
        for (RecordingSchedulerBackend.Delayed d : due) {
            d.task().run();
        }
    }

    private void advanceFlight(int steps) {
        for (int i = 0; i < steps; i++) {
            runDue(1);
        }
    }

    @Test
    void weaponModePricesAtLaunch() {
        Player actor = thrower(-90f, 0f);
        when(weaponDamage.swingDamage(actor)).thenReturn(6.5, 9.0); // priced once at throw; a later swap must not re-price
        LivingEntity victim = cow(new Location(world, 4, 65, 0.5));
        stageNearby(List.of(victim));

        service().start(actor, javelinFx("WEAPON", 7.0)); // step 1 finds the victim → impact

        verify(victim).damage(6.5, actor); // the launch swing, not the 9 read later
    }

    @Test
    void flatModeUsesTheParam() {
        Player actor = thrower(-90f, 0f);
        LivingEntity victim = cow(new Location(world, 4, 65, 0.5));
        stageNearby(List.of(victim));

        service().start(actor, javelinFx("FLAT", 7.0));

        verify(victim).damage(7.0, actor);
        verify(weaponDamage, never()).swingDamage(any());
    }

    @Test
    void stepAdvancesAtSpeedAndBudgetEndsTheMiss() {
        Player actor = thrower(-90f, 0f);
        stageNearby(List.of()); // open lane

        service().start(actor, javelinFx("FLAT", 7.0)); // step 1 runs inline
        advanceFlight(79); // steps 2..80

        assertEquals(0, JavelinService.liveFlightCount());        // 12 / 0.15 = exactly 80 steps then drop
        verify(dispatch, times(80)).dust(any(), anyInt(), anyInt(), anyInt(), anyInt(), anyFloat());
        verify(dispatch, never()).potion(any(), anyInt(), anyInt(), anyInt());
        verify(dispatch, never()).teleportEntity(any(), any());
    }

    @Test
    void wallStopsTheFlight() {
        Player actor = thrower(-90f, 0f);
        stageNearby(List.of()); // nobody in the lane
        Block solid = mock(Block.class);
        when(solid.getType()).thenReturn(Material.STONE);
        AtomicInteger reads = new AtomicInteger();
        when(world.getBlockAt(any(Location.class)))
                .thenAnswer(inv -> reads.incrementAndGet() >= 5 ? solid : airBlock); // solid at step 5

        service().start(actor, javelinFx("FLAT", 7.0));
        advanceFlight(10); // more than enough — the flight dies at step 5

        assertEquals(0, JavelinService.liveFlightCount());
        // Steps 1..4 trail; step 5 = wall → the thud burst (one dust batch at the stop point), no trail.
        verify(dispatch, times(5)).dust(any(), anyInt(), anyInt(), anyInt(), anyInt(), anyFloat());
        verify(dispatch, times(1)).sound(any(), anyInt(), eq(0.7f), anyFloat()); // the 0.7-volume thud layer
        verify(dispatch, never()).teleportEntity(any(), any());
    }

    @Test
    void fullAngleFliesAlongTheFacingPitchIncluded() {
        Player actor = thrower(-90f, 45f); // a hard down-look — the javelin now flies DOWN along it (no yaw-only grounding)
        stageNearby(List.of());
        List<Location> trail = new ArrayList<>();
        doAnswer(inv -> {
            trail.addAll(inv.getArgument(0));
            return null;
        }).when(dispatch).dust(any(), anyInt(), anyInt(), anyInt(), anyInt(), anyFloat());

        service().start(actor, javelinFx("FLAT", 7.0));
        advanceFlight(9); // steps 2..10

        assertEquals(10, trail.size());
        // yaw -90, pitch 45 → dir (√½, -√½, 0); each step advances +X AND descends by the same y-component.
        double comp = 0.15 * Math.sqrt(0.5); // ≈ 0.10607 per axis per step
        assertEquals(0.5 + comp, trail.get(0).getX(), 1.0e-9);
        assertEquals(65 - comp, trail.get(0).getY(), 1.0e-9);
        assertEquals(0.5, trail.get(0).getZ(), 1.0e-9);
        for (int i = 0; i < trail.size(); i++) {
            assertEquals(65 - comp * (i + 1), trail.get(i).getY(), 1.0e-6, "the flight descends along the pitch");
        }
    }

    @Test
    void corneredVictimIsHitBeforeTheWall() {
        Player actor = thrower(-90f, 0f);
        LivingEntity victim = cow(new Location(world, 0.6, 65, 0.5)); // backed against the wall the tip enters
        stageNearby(List.of(victim));
        Block solid = mock(Block.class);
        when(solid.getType()).thenReturn(Material.STONE);
        when(world.getBlockAt(any(Location.class))).thenReturn(solid); // step 1 lands inside terrain

        service().start(actor, javelinFx("FLAT", 7.0));

        verify(victim).damage(7.0, actor); // the scan runs first — the wall behind the victim doesn't shield them
        verify(dispatch, never()).dust(any(), anyInt(), anyInt(), anyInt(), anyInt(), anyFloat()); // no thud burst
        assertEquals(0, JavelinService.liveFlightCount());
    }

    @Test
    void pveOffSkipsMobVictims() {
        Player actor = thrower(-90f, 0f);
        LivingEntity victim = cow(new Location(world, 4, 65, 0.5));
        stageNearby(List.of(victim));

        service(() -> true, () -> false, (t, v) -> false).start(actor, javelinFx("FLAT", 7.0));
        advanceFlight(3);

        verify(victim, never()).damage(anyDouble(), any());
        assertEquals(1, JavelinService.liveFlightCount()); // the javelin flies on past the gated candidate
    }

    @Test
    void pvpOffSkipsPlayerVictims() {
        Player actor = thrower(-90f, 0f);
        Player victim = playerVictim(new Location(world, 4, 65, 0.5));
        stageNearby(List.of(victim));

        service(() -> false, () -> true, (t, v) -> false).start(actor, javelinFx("FLAT", 7.0));
        advanceFlight(3);

        verify(victim, never()).damage(anyDouble(), any());
        assertEquals(1, JavelinService.liveFlightCount());
    }

    @Test
    void friendlyPlayerIsNeverTheVictim() {
        Player actor = thrower(-90f, 0f);
        Player mate = playerVictim(new Location(world, 4, 65, 0.5));
        stageNearby(List.of(mate));

        service(() -> true, () -> true, (t, v) -> t == actor && v == mate).start(actor, javelinFx("FLAT", 7.0));
        advanceFlight(3);

        verify(mate, never()).damage(anyDouble(), any());
        assertEquals(1, JavelinService.liveFlightCount());
    }

    @Test
    void hostilePlayerVictimIsHitUnderPvp() {
        Player actor = thrower(-90f, 0f);
        Player victim = playerVictim(new Location(world, 4, 65, 0.5));
        stageNearby(List.of(victim));

        service().start(actor, javelinFx("FLAT", 7.0));

        verify(victim).damage(7.0, actor);
        assertEquals(0, JavelinService.liveFlightCount());
    }

    @Test
    void armorStandsAreSkippedByTheScan() {
        Player actor = thrower(-90f, 0f);
        LivingEntity stand = mock(ArmorStand.class);
        when(stand.getUniqueId()).thenReturn(UUID.randomUUID());
        LivingEntity victim = cow(new Location(world, 4, 65, 0.5));
        stageNearby(List.of(stand, victim)); // the stand is closer in the list — must be passed over

        service().start(actor, javelinFx("FLAT", 7.0));

        verify(stand, never()).damage(anyDouble(), any());
        verify(victim).damage(7.0, actor);
    }

    @Test
    void firstLivingOnPathIsHitOnceThrowerExcluded() {
        Player actor = thrower(-90f, 0f);
        when(weaponDamage.swingDamage(actor)).thenReturn(5.0);
        LivingEntity target = cow(new Location(world, 4, 65, 0.5));
        stageNearby(List.of(actor, target)); // the thrower's own body is in range but must be skipped

        service().start(actor, javelinFx("WEAPON", 7.0));

        verify(target).damage(5.0, actor);
        verify(actor, never()).damage(anyDouble(), any()); // the thrower is never his own victim
        assertEquals(0, JavelinService.liveFlightCount()); // the flight ends on the hit
    }

    @Test
    void knockbackIsAlongTheFlightAngleTimesFactor() {
        Player actor = thrower(30f, -20f); // pitched look — the knock rides the FULL flight angle (pitch included)
        when(weaponDamage.swingDamage(actor)).thenReturn(5.0);
        LivingEntity victim = cow(new Location(world, 1, 66, 1));
        stageNearby(List.of(victim));

        Vector expected = actor.getEyeLocation().getDirection().multiply(0.45 * 1.3); // base × factor along the full angle
        expected.setY(Math.max(0.12, expected.getY() + 0.12)); // a touch of lift

        service().start(actor, javelinFx("WEAPON", 7.0));

        ArgumentCaptor<Vector> kb = ArgumentCaptor.forClass(Vector.class);
        verify(victim).setVelocity(kb.capture());
        assertEquals(expected.getX(), kb.getValue().getX(), 1.0e-6);
        assertEquals(expected.getY(), kb.getValue().getY(), 1.0e-6);
        assertEquals(expected.getZ(), kb.getValue().getZ(), 1.0e-6);
    }

    @Test
    void secondImpactMidHoldReplacesThePin() {
        Player actor = thrower(-90f, 0f);
        when(weaponDamage.swingDamage(actor)).thenReturn(5.0);
        LivingEntity victim = cow(new Location(world, 4, 65, 0.5, 0f, 0f));
        stageNearby(List.of(victim));

        service().start(actor, javelinFx("WEAPON", 7.0));
        runDue(5); // pin #1 arms
        RecordingSchedulerBackend.Repeat firstPin = backend.repeating.get(0);

        service().start(actor, javelinFx("WEAPON", 7.0)); // a second javelin hits the same victim
        runDue(5); // pin #2 arms → must cancel pin #1 (refresh-not-stack)

        assertTrue(firstPin.isCancelled(), "the earlier pin is cancelled, not stacked");
        assertEquals(1, ControlLockService.lockCount()); // the extracted lock refresh-not-stacks
    }

    @Test
    void nauseaArmsAfterTheHoldReleases() {
        Player actor = thrower(-90f, 0f);
        when(weaponDamage.swingDamage(actor)).thenReturn(5.0);
        LivingEntity victim = cow(new Location(world, 4, 65, 0.5));
        stageNearby(List.of(victim));

        service().start(actor, javelinFx("WEAPON", 7.0));

        verify(dispatch, never()).potion(any(), anyInt(), anyInt(), anyInt()); // NOT at impact (the lock lands first)
        runDue(25); // lock-delay 5 + lock 20 — the sequenced nausea moment
        verify(dispatch).potion(victim, 9, 0, 100); // Nausea I for the authored duration, after the hold
    }

    @Test
    void nauseaSkippedWhenVictimInvalid() {
        Player actor = thrower(-90f, 0f);
        when(weaponDamage.swingDamage(actor)).thenReturn(5.0);
        AtomicBoolean valid = new AtomicBoolean(true);
        LivingEntity victim = cow(new Location(world, 4, 65, 0.5));
        when(victim.isValid()).thenAnswer(inv -> valid.get());
        stageNearby(List.of(victim));

        service().start(actor, javelinFx("WEAPON", 7.0)); // impact while valid
        valid.set(false); // the victim died/left during the lock
        runDue(25);

        verify(dispatch, never()).potion(any(), anyInt(), anyInt(), anyInt());
    }

    @Test
    void boundaryStepScanFailsClosed() {
        Player actor = thrower(-90f, 0f);
        when(weaponDamage.swingDamage(actor)).thenReturn(5.0);
        LivingEntity victim = cow(new Location(world, 4, 65, 0.5));
        // Step 1's scan faults (a Folia off-region read); the next re-keyed step re-covers and hits.
        when(world.getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble()))
                .thenThrow(new IllegalStateException("cross-region")).thenReturn(List.of(victim));

        service().start(actor, javelinFx("WEAPON", 7.0)); // step 1: guarded scan fails closed, no impact, re-arm
        verify(victim, never()).damage(anyDouble(), any());
        advanceFlight(1); // step 2: the sliver is re-covered → hit

        verify(victim, times(1)).damage(5.0, actor);
        assertEquals(0, JavelinService.liveFlightCount());
    }

    @Test
    void clearAllReleasesFlightsAndControlLocks() {
        Player actor = thrower(-90f, 0f);
        when(weaponDamage.swingDamage(actor)).thenReturn(5.0);
        List<Entity> nearby = new ArrayList<>();
        LivingEntity victim = cow(new Location(world, 4, 65, 0.5));
        nearby.add(victim);
        when(world.getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble())).thenReturn(nearby);

        service().start(actor, javelinFx("WEAPON", 7.0)); // hits → impact → control-lock armed
        runDue(5); // lock armed
        assertEquals(1, ControlLockService.lockCount());

        nearby.clear(); // the lane is now open
        Player second = mock(Player.class);
        when(second.getUniqueId()).thenReturn(UUID.randomUUID());
        when(second.isValid()).thenReturn(true);
        when(second.getEyeLocation()).thenReturn(new Location(world, 20, 65, 20, -90f, 0f));
        service().start(second, javelinFx("FLAT", 7.0)); // misses → a live flight persists
        assertEquals(1, JavelinService.liveFlightCount());

        JavelinService.clearAll();            // the flight stop clears flights…
        ControlLockService.clearAll();        // …the control-lock stop clears held victims (both wired on disable)

        assertEquals(0, JavelinService.liveFlightCount());
        assertEquals(0, ControlLockService.lockCount());
        assertTrue(backend.repeating.get(0).isCancelled()); // the lock task is released, never stranded
    }
}
