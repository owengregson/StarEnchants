package engine.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import platform.resolve.RegistryResolvers;
import platform.resolve.RuntimeHandles;
import platform.sched.Scheduling;
import testfx.Envs;
import testfx.RecordingSchedulerBackend;

/**
 * The ADR-0054 same-hit rider routing: a zero-WAIT damage intent aimed at the declared event entity joins
 * the {@link engine.interact.DamageFold} instead of issuing a second {@code hurt()}. A bare second hurt
 * re-arms the victim's vanilla immunity window ({@code noDamageTicks}/{@code lastHurt}), so the NEXT melee
 * with a smaller amount is window-rejected with no event at all — silently eating other plugins' per-hit
 * handling (the Mental-compat defect this pins shut). Everything that is genuinely a separate proc — a
 * WAIT tier, a bystander target, a hit with no declared event entity — keeps the deferred entity hop.
 */
@SuppressWarnings("deprecation") // getMaxHealth(): the era fallback leaf the mock path resolves through.
class DispatchSinkDamageFoldTest {

    private RuntimeHandles handles;
    private RecordingSchedulerBackend backend;

    @BeforeEach
    void setUp() {
        handles = new RuntimeHandles(new RegistryResolvers());
        backend = new RecordingSchedulerBackend();
        Scheduling.install(backend);
    }

    private static LivingEntity aliveEntity() {
        LivingEntity entity = mock(LivingEntity.class);
        when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
        when(entity.isValid()).thenReturn(true);
        when(entity.isDead()).thenReturn(false);
        when(entity.getHealth()).thenReturn(10.0);
        when(entity.getMaxHealth()).thenReturn(20.0);
        return entity;
    }

    @Test
    void aZeroWaitDamageToTheEventEntityJoinsTheFoldAndNeverHurtsTwice() {
        LivingEntity victim = aliveEntity();

        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().build());
        sink.eventEntity(victim);
        sink.damage(victim, 6.0);

        // The rider rides the ONE event: it lands as a flat fold contribution the dispatcher commits...
        assertEquals(6.0, sink.fold().flatDamage(), 1e-9);
        assertEquals(16.0, sink.fold().apply(10.0), 1e-9);
        // ...and no second hurt ever fires, so the victim's immunity window is re-armed exactly once.
        sink.flush();
        verify(victim, never()).damage(anyDouble());
        verify(victim, never()).damage(anyDouble(), any(Entity.class));
    }

    @Test
    void theEventEntityIsMatchedByUuidNotByInstance() {
        LivingEntity victim = aliveEntity();
        UUID sharedId = victim.getUniqueId();
        LivingEntity rewrapped = aliveEntity();
        when(rewrapped.getUniqueId()).thenReturn(sharedId);

        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().build());
        sink.eventEntity(victim);
        sink.damage(rewrapped, 4.0);

        assertEquals(4.0, sink.fold().flatDamage(), 1e-9); // still the rider: same entity, different handle
        sink.flush();
        verify(rewrapped, never()).damage(anyDouble());
    }

    @Test
    void aBystanderDamageStaysADeferredSeparateHit() {
        LivingEntity victim = aliveEntity();
        LivingEntity bystander = aliveEntity();

        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().build());
        sink.eventEntity(victim);
        sink.damage(bystander, 7.5);

        assertEquals(0.0, sink.fold().flatDamage(), 1e-9); // an AoE bystander is not this event's victim
        sink.flush();
        verify(bystander).damage(7.5);
    }

    @Test
    void aWaitedDamageToTheEventEntityStaysASeparateProc() {
        // THE bleed shape: the DoT tier fires ticks after the hit's event is gone, so it cannot ride the
        // fold — it must stay its own application (and its own vanilla immunity window, like any real hit).
        LivingEntity victim = aliveEntity();

        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().build());
        sink.eventEntity(victim);
        sink.delay(20);
        sink.damage(victim, 2.0);
        sink.flush();

        assertEquals(0.0, sink.fold().flatDamage(), 1e-9);
        assertEquals(1, backend.delayed.size(), "a WAIT damage is never a fold contribution");
        backend.runDelayed();
        verify(victim).damage(2.0);
    }

    @Test
    void percentOfMaxToTheEventEntityFoldsOffItsLiveMaxHealth() {
        // The firing thread owns the event entity by definition (the ADR-0051 argument), so the
        // max-health read is region-correct inline: 10% of 20 folds as a flat 2.0.
        LivingEntity victim = aliveEntity();

        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().build());
        sink.eventEntity(victim);
        sink.damagePercentOfMax(victim, 10.0);

        assertEquals(2.0, sink.fold().flatDamage(), 1e-9);
        sink.flush();
        verify(victim, never()).damage(anyDouble());
    }

    @Test
    void percentOfMaxToABystanderKeepsTheDeferredHop() {
        LivingEntity victim = aliveEntity();
        LivingEntity bystander = aliveEntity();

        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().build());
        sink.eventEntity(victim);
        sink.damagePercentOfMax(bystander, 25.0);

        assertEquals(0.0, sink.fold().flatDamage(), 1e-9);
        sink.flush();
        verify(bystander).damage(5.0); // 25% of max 20, computed on the target's own thread
    }

    @Test
    void withNoEventEntityDeclaredEveryDamageKeepsTheDeferredHop() {
        // Non-combat dispatchers never declare an event entity (ADR-0051), so nothing folds there —
        // the rider carve-out is scoped to combat events only.
        LivingEntity target = aliveEntity();

        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().build());
        sink.damage(target, 3.0);

        assertEquals(0.0, sink.fold().flatDamage(), 1e-9);
        sink.flush();
        verify(target).damage(3.0);
        Mockito.verify(target, never()).damage(anyDouble(), any(Entity.class));
    }

    // ── ADR-0054 attribution: deferred applications carry the attacker, so downstream plugins see who dealt it ──

    @Test
    void aDeferredDamageWithAnAttackerInScopeFiresAttributed() {
        LivingEntity victim = aliveEntity();
        LivingEntity bystander = aliveEntity();
        LivingEntity attacker = aliveEntity();

        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().build());
        sink.eventEntity(victim);
        sink.damage(bystander, 7.5, attacker);
        sink.flush();

        verify(bystander).damage(7.5, attacker); // an attributed EntityDamageByEntityEvent downstream
        verify(bystander, never()).damage(anyDouble());
    }

    @Test
    void aWaitedEventEntityDamageCarriesTheAttribution() {
        // The bleed shape with its owner: the DoT tick is a separate hit, but it is the ATTACKER's hit.
        LivingEntity victim = aliveEntity();
        LivingEntity attacker = aliveEntity();

        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().build());
        sink.eventEntity(victim);
        sink.delay(20);
        sink.damage(victim, 2.0, attacker);
        sink.flush();

        assertEquals(0.0, sink.fold().flatDamage(), 1e-9);
        backend.runDelayed();
        verify(victim).damage(2.0, attacker);
    }

    @Test
    void percentOfMaxToABystanderCarriesTheAttribution() {
        LivingEntity victim = aliveEntity();
        LivingEntity bystander = aliveEntity();
        LivingEntity attacker = aliveEntity();

        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().build());
        sink.eventEntity(victim);
        sink.damagePercentOfMax(bystander, 25.0, attacker);
        sink.flush();

        verify(bystander).damage(5.0, attacker); // 25% of max 20, attributed
    }

    @Test
    void selfAttributionFallsBackToTheBareHurt() {
        // A self-damaging effect attributing itself would read as a self-hit (combat dispatchers drop
        // damager == victim anyway) — keep it the ownerless application it always was.
        LivingEntity target = aliveEntity();

        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().build());
        sink.damage(target, 3.0, target);
        sink.flush();

        verify(target).damage(3.0);
        verify(target, never()).damage(anyDouble(), any(Entity.class));
    }

    @Test
    void lightningNeverFoldsAndCarriesTheAttribution() {
        // A bolt is its own proc, never a same-hit rider — even aimed at the event entity it stays a
        // separate, attributed application.
        LivingEntity victim = aliveEntity();
        LivingEntity attacker = aliveEntity();
        World world = mock(World.class);
        Location at = mock(Location.class);
        when(victim.getWorld()).thenReturn(world);
        when(victim.getLocation()).thenReturn(at);

        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().build());
        sink.eventEntity(victim);
        sink.lightningAndDamage(victim, 6.0, attacker);

        assertEquals(0.0, sink.fold().flatDamage(), 1e-9);
        sink.flush();
        verify(world).strikeLightning(at);
        verify(victim).damage(6.0, attacker);
    }

    @Test
    void engineIssuedDamageRunsInsideTheEngineFrame() {
        // The re-entrancy contract the old bare hurt() enforced structurally: while OUR damage applies,
        // EngineDamage.active() is true, so SE's own combat listeners stand down for the events it fires.
        LivingEntity bystander = aliveEntity();
        LivingEntity attacker = aliveEntity();
        boolean[] activeInside = {false};
        Mockito.doAnswer(inv -> {
            activeInside[0] = EngineDamage.active();
            return null;
        }).when(bystander).damage(5.0, attacker);

        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().build());
        sink.damage(bystander, 5.0, attacker);
        sink.flush();

        assertTrue(activeInside[0], "the attributed application must run inside the engine-damage frame");
        assertTrue(!EngineDamage.active(), "the frame always unwinds after the application");
    }
}
