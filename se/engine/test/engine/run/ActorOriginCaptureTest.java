package engine.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import compile.model.Ability;
import compile.model.Affinity;
import compile.model.CompiledEffect;
import compile.model.CompiledSelector;
import compile.model.StableKeyIndex;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.effect.EffectRegistry;
import engine.effect.kind.IgniteEffect;
import engine.interact.SoulSpender;
import engine.pipeline.Activation;
import engine.pipeline.ActivationPipeline;
import engine.selector.SelectorRegistry;
import engine.selector.kind.SelfSelector;
import engine.selector.kind.VictimSelector;
import engine.sink.ModernDispatchSink;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import engine.stores.CooldownStore;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import platform.resolve.RegistryResolvers;
import platform.resolve.RuntimeHandles;
import platform.sched.Scheduling;
import schema.spec.Args;
import testfx.Abilities;
import testfx.Envs;
import testfx.SyncSchedulerBackend;

/**
 * The executor's ADR-0043 actor-origin capture: only an {@code actorOrigin()}-flagged kind triggers a
 * firing-thread snapshot, the snapshot carries the actor's full pose, a cross-region capture fault fails
 * closed (never an effect fault), and the lifecycle path captures too. Wires REAL engine components, mocking
 * only the Bukkit actor.
 */
class ActorOriginCaptureTest {

    private static final UUID ACTOR = UUID.randomUUID();
    private static final int TRIGGER = 0;
    private static final StableKeyIndex KEYS = new StableKeyIndex(List.of("enchants/probe/1"));

    private OriginProbeKind probe;
    private RuntimeHandles handles;
    private AbilityExecutor executor;

    @BeforeEach
    void setUp() {
        handles = new RuntimeHandles(new RegistryResolvers());
        Scheduling.install(new SyncSchedulerBackend());
        probe = new OriginProbeKind();
        EffectRegistry effects = EffectRegistry.builder().register(probe).register(new IgniteEffect()).build();
        SelectorRegistry selectors = SelectorRegistry.builder()
                .register(new SelfSelector()).register(new VictimSelector()).build();
        ActivationPipeline pipeline = new ActivationPipeline(new CooldownStore(), SoulSpender.NONE);
        executor = new AbilityExecutor(effects, selectors, pipeline, AreaScan.NONE);
    }

    @Test
    void flaggedKindSeesTheOriginSnapshot() {
        World world = mock(World.class);
        Player actor = mock(Player.class);
        when(actor.getLocation()).thenReturn(new Location(world, 3, 4, 5, 90f, 10f));
        when(actor.getEyeHeight()).thenReturn(1.62);
        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().build());

        int activated = executor.run(new Ability[] {probeAbility()}, new int[] {0}, activation(),
                context(actor, null), sink, KEYS);

        assertEquals(1, activated);
        assertEquals(1, probe.runs.get());
        Location feet = probe.feet.get();
        assertNotNull(feet);
        assertEquals(3.0, feet.getX(), 1.0e-9);
        assertEquals(4.0, feet.getY(), 1.0e-9);
        assertEquals(5.0, feet.getZ(), 1.0e-9);
        assertEquals(90.0f, feet.getYaw(), 1.0e-6);
        assertEquals(10.0f, feet.getPitch(), 1.0e-6);
        assertSame(world, feet.getWorld());
        assertEquals(5.62, probe.eye.get().getY(), 1.0e-9); // feet.y + captured eye height
    }

    @Test
    void unflaggedKindNeverTriggersACapture() {
        Player actor = mock(Player.class); // getLocation() left unstubbed — the demand bit must never call it
        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().build());

        int activated = executor.run(new Ability[] {igniteAbility()}, new int[] {0}, activation(),
                context(actor, null), sink, KEYS);

        assertEquals(1, activated);
        verify(actor, never()).getLocation();
    }

    @Test
    void captureFaultFailsClosedWithoutAFault() {
        Player actor = mock(Player.class);
        when(actor.getLocation()).thenThrow(new IllegalStateException("wrong region"));
        executor.bindQuarantine(new AbilityQuarantine(null, KEYS, 1));
        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().build());

        int activated = executor.run(new Ability[] {probeAbility()}, new int[] {0}, activation(),
                context(actor, null), sink, KEYS);

        assertEquals(1, activated);
        assertEquals(1, probe.runs.get());
        assertNull(probe.feet.get()); // uncapturable origin → null, the kind fails closed
        assertTrue(executor.quarantinedKeys().isEmpty()); // a swallowed capture is not an effect fault
    }

    @Test
    void lifecycleRunAlsoCaptures() {
        World world = mock(World.class);
        Player actor = mock(Player.class);
        when(actor.getLocation()).thenReturn(new Location(world, 1, 2, 3));
        when(actor.getEyeHeight()).thenReturn(1.62);
        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().build());

        executor.runLifecycle(probeAbility(), context(actor, null), sink, false);

        assertNotNull(probe.feet.get());
        assertEquals(1.0, probe.feet.get().getX(), 1.0e-9);
    }

    private static Ability probeAbility() {
        return Abilities.ability().trigger(TRIGGER).affinity(Affinity.REGION)
                .effects(new CompiledEffect("ORIGIN_PROBE", Args.empty(),
                        new CompiledSelector("SELF", Args.empty()), 0, Affinity.REGION)).build();
    }

    private static Ability igniteAbility() {
        return Abilities.ability().trigger(TRIGGER).affinity(Affinity.TARGET_ENTITY)
                .effects(new CompiledEffect("IGNITE", Args.empty().with("duration", 40L),
                        new CompiledSelector("SELF", Args.empty()), 0, Affinity.TARGET_ENTITY)).build();
    }

    private static Activation activation() {
        return Activation.builder(ACTOR, 0, TRIGGER, 0L).build();
    }

    private static ActivationContext context(Player actor, LivingEntity victim) {
        return new ActivationContext(actor, victim, null, null);
    }

    /** Records the origin snapshot it is handed, so a test can assert the executor captured before running it. */
    private static final class OriginProbeKind implements EffectKind {
        static final EffectSpec SPEC = EffectSpec.of("ORIGIN_PROBE")
                .target("who", T.SELF).affinity(Affinity.REGION).actorOrigin().build();
        final AtomicReference<Location> feet = new AtomicReference<>();
        final AtomicReference<Location> eye = new AtomicReference<>();
        final AtomicInteger runs = new AtomicInteger();

        @Override
        public EffectSpec spec() {
            return SPEC;
        }

        @Override
        public void run(EffectCtx ctx, Sink sink) {
            runs.incrementAndGet();
            feet.set(ctx.actorOrigin());
            eye.set(ctx.actorOriginEye());
        }
    }
}
