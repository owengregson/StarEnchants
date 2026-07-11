package engine.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.mockito.InOrder;
import platform.resolve.RegistryResolvers;
import platform.resolve.RuntimeHandles;
import platform.sched.Scheduling;
import testfx.Envs;
import testfx.RecordingSchedulerBackend;

/**
 * The ADR-0051 current-health routing: a zero-WAIT heal/setHealth to the declared event entity runs inline at
 * flush BEFORE the deferred plan (so it precedes the firing event's own kill decision), and every deferred
 * health write is liveness-gated at execution time — a target that died first drops the write instead of being
 * revived into a half-dead ghost. The real death-ordering against a vanilla kill decision is matrix-verified
 * (DeathRaceSuite); these pin the routing and the gate.
 */
@SuppressWarnings("deprecation") // getMaxHealth(): the era fallback leaf the mock path resolves through.
class DispatchSinkHealthCreditTest {

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
        when(entity.getHealth()).thenReturn(4.0);
        when(entity.getMaxHealth()).thenReturn(20.0);
        return entity;
    }

    @Test
    void aZeroWaitHealToTheEventEntityJumpsAheadOfTheDeferredPlanAtFlush() {
        LivingEntity victim = aliveEntity();

        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().build());
        sink.eventEntity(victim);
        sink.ignite(victim, 60);  // deferred plan op, emitted FIRST
        sink.heal(victim, 10.0);  // health credit, emitted SECOND

        sink.flush();
        // Inline credits run before the plan: the heal overtakes the earlier-emitted ignite. Were the heal
        // deferred, the same-entity batch would preserve emission order (ignite first) — the ghost-maker.
        InOrder order = inOrder(victim);
        order.verify(victim).setHealth(14.0); // 4 + 10, clamped to max 20 via the fallback leaf
        order.verify(victim).setFireTicks(60);
    }

    @Test
    void theEventEntityIsMatchedByUuidNotByInstance() {
        LivingEntity victim = aliveEntity();
        UUID sharedId = victim.getUniqueId();
        LivingEntity rewrapped = aliveEntity();
        when(rewrapped.getUniqueId()).thenReturn(sharedId);

        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().build());
        sink.eventEntity(victim);
        sink.ignite(rewrapped, 60);
        sink.heal(rewrapped, 10.0);

        sink.flush();
        InOrder order = inOrder(rewrapped);
        order.verify(rewrapped).setHealth(14.0); // still a credit: same entity, different handle
        order.verify(rewrapped).setFireTicks(60);
    }

    @Test
    void aBystanderHealStaysDeferredInTheEntityBatchInEmissionOrder() {
        LivingEntity victim = aliveEntity();
        LivingEntity bystander = aliveEntity();

        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().build());
        sink.eventEntity(victim);
        sink.ignite(bystander, 60);
        sink.heal(bystander, 10.0);

        sink.flush();
        InOrder order = inOrder(bystander);
        order.verify(bystander).setFireTicks(60); // emission order kept: not the event entity ⇒ no credit
        order.verify(bystander).setHealth(14.0);
    }

    @Test
    void aSecondFlushNeverRerunsTheCredits() {
        LivingEntity victim = aliveEntity();

        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().build());
        sink.eventEntity(victim);
        sink.heal(victim, 10.0);

        sink.flush();
        sink.flush();
        verify(victim).setHealth(14.0); // exactly once
    }

    @Test
    void aWaitedHealToTheEventEntityDefersAndDropsWhenTheTargetDiedFirst() {
        // THE half-death shape: the WAIT tier fires after the lethal hit resolved. The gate must read the
        // target's state at execution time — it was alive at emit time.
        LivingEntity victim = aliveEntity();

        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().build());
        sink.eventEntity(victim);
        sink.delay(20);
        sink.heal(victim, 10.0);
        sink.flush();

        verify(victim, never()).setHealth(anyDouble());
        assertEquals(1, backend.delayed.size(), "a WAIT heal is never an inline credit");

        when(victim.isDead()).thenReturn(true); // the hit killed them before the tier fired
        when(victim.getHealth()).thenReturn(0.0);
        backend.runDelayed();
        verify(victim, never()).setHealth(anyDouble());
    }

    @Test
    void aWaitedHealToAStillLivingTargetLandsWhenItsTierFires() {
        LivingEntity victim = aliveEntity();

        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().build());
        sink.eventEntity(victim);
        sink.delay(20);
        sink.heal(victim, 10.0);
        sink.flush();

        backend.runDelayed();
        verify(victim).setHealth(14.0); // the gate only drops writes to the dead, never delays the living
    }

    @TestFactory
    List<DynamicTest> aHealthWriteLandingOnADeadTargetIsDropped() {
        // Rows: each way a target can be "dead" at execution time × both current-health ops. getHealth()==0
        // carries the 1.8 lane, where isDead() can lag the dead flag; !isValid() covers removed entities.
        record Dead(String name, Consumer<LivingEntity> stub) {
        }
        List<Dead> deads = List.of(
                new Dead("isDead", e -> when(e.isDead()).thenReturn(true)),
                new Dead("zeroHealth", e -> when(e.getHealth()).thenReturn(0.0)),
                new Dead("invalid", e -> when(e.isValid()).thenReturn(false)));
        record Op(String name, BiConsumer<ModernDispatchSink, LivingEntity> emit) {
        }
        List<Op> ops = List.of(
                new Op("heal", (sink, e) -> sink.heal(e, 10.0)),
                new Op("setHealth", (sink, e) -> sink.setHealth(e, 10.0)));

        List<DynamicTest> tests = new java.util.ArrayList<>();
        for (Dead dead : deads) {
            for (Op op : ops) {
                tests.add(dynamicTest(op.name() + " dropped on " + dead.name(), () -> {
                    LivingEntity target = aliveEntity();
                    dead.stub().accept(target);

                    ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().build());
                    op.emit().accept(sink, target); // a bystander: the deferred, gated path
                    sink.flush();
                    verify(target, never()).setHealth(anyDouble());
                }));
            }
        }
        // The credit path is gated too: a double-fired event on an already-dying event entity must not revive it.
        tests.add(dynamicTest("credit dropped on a dying event entity", () -> {
            LivingEntity victim = aliveEntity();
            when(victim.isDead()).thenReturn(true);

            ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().build());
            sink.eventEntity(victim);
            sink.heal(victim, 10.0);
            sink.flush();
            verify(victim, never()).setHealth(anyDouble());
        }));
        return tests;
    }

    @Test
    void setHealthToTheEventEntityIsACreditWithTheSetModeClamp() {
        LivingEntity victim = aliveEntity();

        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().build());
        sink.eventEntity(victim);
        sink.ignite(victim, 60);
        sink.setHealth(victim, 35.0); // set mode clamps to [0, max]

        sink.flush();
        InOrder order = inOrder(victim);
        order.verify(victim).setHealth(20.0);
        order.verify(victim).setFireTicks(60);
        assertTrue(backend.delayed.isEmpty(), "a zero-WAIT credit never schedules a delayed task");
    }
}
