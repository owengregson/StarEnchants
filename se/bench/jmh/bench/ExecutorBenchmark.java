package bench;

import compile.model.Ability;
import compile.stage.ErasedContent;
import engine.effect.EffectRegistry;
import engine.interact.SoulSpender;
import engine.pipeline.Activation;
import engine.pipeline.ActivationPipeline;
import engine.run.AbilityExecutor;
import engine.run.ActivationContext;
import engine.run.AreaScan;
import engine.selector.SelectorRegistry;
import compile.model.StableKeyIndex;
import engine.sink.DispatchSink;
import engine.stores.CooldownStore;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import platform.resolve.RegistryResolvers;
import platform.resolve.RuntimeHandles;
import platform.sched.Scheduling;

/** Effect execution (§3.5, §8): the executor runs one activated ability's effect into a reused sink. */
@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class ExecutorBenchmark {

    private AbilityExecutor executor;
    private Ability[] abilities;
    private int[] candidates;
    private Activation activation;
    private ActivationContext context;
    private DispatchSink sink;
    private StableKeyIndex keys;

    @Setup
    public void setUp() {
        Scheduling.install(new BenchWorld.InlineScheduler());
        ErasedContent erased = BenchWorld.erased();
        EffectRegistry effects = EffectRegistry.builder().register(new BenchWorld.NoopEffect()).build();
        SelectorRegistry selectors = SelectorRegistry.builder().register(new BenchWorld.NoopSelector()).build();
        executor = new AbilityExecutor(effects, selectors,
                new ActivationPipeline(new CooldownStore(), SoulSpender.NONE), AreaScan.NONE);
        abilities = erased.abilities();
        candidates = new int[] {0};
        int triggerId = BenchWorld.triggerId(erased.interners());
        activation = Activation.builder(UUID.randomUUID(), 0, triggerId, 0L).build();
        context = new ActivationContext(null, null, null, null);
        keys = new StableKeyIndex(List.of("enchants/bench"));
        // Reused across ops: the no-op effect emits nothing, so the sink's plan never fills and needs no flush.
        sink = new DispatchSink(new RuntimeHandles(new RegistryResolvers()));
    }

    @Benchmark
    public int effectExecution() {
        return executor.run(abilities, candidates, activation, context, sink, keys);
    }
}
