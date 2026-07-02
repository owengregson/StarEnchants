package engine.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import compile.model.Ability;
import compile.model.Affinity;
import compile.model.CompiledEffect;
import compile.model.CompiledSelector;
import compile.model.SourceKind;
import compile.model.SourceMap;
import compile.model.StableKeyIndex;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.effect.EffectRegistry;
import engine.interact.SoulSpender;
import engine.pipeline.Activation;
import engine.pipeline.ActivationPipeline;
import engine.selector.SelectorRegistry;
import engine.selector.kind.SelfSelector;
import engine.sink.DispatchSink;
import engine.sink.Sink;
import engine.sink.SyncSchedulerBackend;
import engine.spec.EffectSpec;
import engine.spec.T;
import engine.stores.CooldownStore;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import platform.resolve.RegistryResolvers;
import platform.resolve.RuntimeHandles;
import platform.sched.Scheduling;
import schema.diag.Source;
import schema.spec.Args;

/**
 * The §10 runtime quarantine: an ability whose effect keeps faulting is disabled for the life of the
 * snapshot after {@link #THRESHOLD} failures, and the block resets when a reload binds a fresh snapshot.
 */
class AbilityQuarantineTest {

    private static final int THRESHOLD = 3;
    private static final UUID ACTOR = UUID.randomUUID();
    private static final int TRIGGER = 0;
    private static final StableKeyIndex KEYS = new StableKeyIndex(List.of("enchants/boom"));
    private static final SourceMap SOURCES = new SourceMap(Map.of(
            0, new SourceMap.Entry(SourceKind.ENCHANT, "enchants/boom", Source.of("enchants/boom.yml", 5, 3))));

    /** Counts invocations so a skipped (quarantined) activation is observable, then throws to force a fault. */
    private static final class ThrowingEffect implements EffectKind {
        final AtomicInteger runs = new AtomicInteger();
        private final EffectSpec spec = EffectSpec.of("BOOM").target("who", T.SELF)
                .affinity(Affinity.TARGET_ENTITY).build();

        @Override
        public EffectSpec spec() {
            return spec;
        }

        @Override
        public void run(EffectCtx ctx, Sink sink) {
            runs.incrementAndGet();
            throw new IllegalStateException("boom");
        }
    }

    private RuntimeHandles handles;
    private ThrowingEffect boom;
    private AbilityExecutor executor;

    @BeforeEach
    void setUp() {
        handles = new RuntimeHandles(new RegistryResolvers());
        Scheduling.install(new SyncSchedulerBackend());
        boom = new ThrowingEffect();
        EffectRegistry effects = EffectRegistry.builder().register(boom).build();
        SelectorRegistry selectors = SelectorRegistry.builder().register(new SelfSelector()).build();
        ActivationPipeline pipeline = new ActivationPipeline(new CooldownStore(), SoulSpender.NONE);
        executor = new AbilityExecutor(effects, selectors, pipeline, AreaScan.NONE);
    }

    @Test
    void quarantinesAfterThresholdFailuresThenSkipsBeforeEffectsRun() {
        executor.bindQuarantine(new AbilityQuarantine(SOURCES, KEYS, THRESHOLD));
        Ability[] abilities = {boom()};

        for (int i = 0; i < THRESHOLD; i++) {
            fire(abilities);
        }
        assertEquals(THRESHOLD, boom.runs.get(), "each of the first N activations runs (and faults)");
        assertEquals(List.of("enchants/boom"), executor.quarantinedKeys());

        fire(abilities); // N+1th
        assertEquals(THRESHOLD, boom.runs.get(), "quarantined: skipped before the effect runs");
    }

    @Test
    void quarantineDoesNotLeakAcrossASnapshotSwap() {
        executor.bindQuarantine(new AbilityQuarantine(SOURCES, KEYS, THRESHOLD));
        Ability[] abilities = {boom()};
        for (int i = 0; i < THRESHOLD; i++) {
            fire(abilities);
        }
        assertTrue(executor.quarantinedKeys().contains("enchants/boom"));

        executor.bindQuarantine(new AbilityQuarantine(SOURCES, KEYS, THRESHOLD)); // reload → fresh snapshot
        assertEquals(List.of(), executor.quarantinedKeys());

        fire(abilities);
        assertEquals(THRESHOLD + 1, boom.runs.get(), "the fresh snapshot re-runs the ability, no leaked block");
    }

    private void fire(Ability[] abilities) {
        Player actor = mock(Player.class);
        DispatchSink sink = new DispatchSink(handles);
        executor.run(abilities, new int[] {0}, Activation.builder(ACTOR, 0, TRIGGER, 0L).build(),
                new ActivationContext(actor, null, null, null), sink, KEYS);
        sink.flush();
    }

    private static Ability boom() {
        return new Ability(0, 0, SourceKind.ENCHANT, 1 << TRIGGER, 1, 100.0, 0, 0, 0L, null,
                new CompiledEffect[] {new CompiledEffect("BOOM", Args.empty(),
                        new CompiledSelector("SELF", Args.empty()), 0, Affinity.TARGET_ENTITY)},
                0, Affinity.TARGET_ENTITY, -1, -1, -1, -1, 0);
    }
}
