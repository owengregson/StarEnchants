package bench;

import compile.model.Ability;
import compile.stage.ErasedContent;
import engine.interact.SoulSpender;
import engine.pipeline.Activation;
import engine.pipeline.ActivationPipeline;
import engine.stores.CooldownStore;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/** The gate-walk hot path (§3.3, §8): one ability through the full {@link ActivationPipeline}, allocation-light. */
@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class PipelineBenchmark {

    private ActivationPipeline pipeline;
    private Ability ability;
    private Activation activation;

    @Setup
    public void setUp() {
        ErasedContent erased = BenchWorld.erased();
        pipeline = new ActivationPipeline(new CooldownStore(), SoulSpender.NONE);
        ability = erased.abilities()[0];
        int triggerId = BenchWorld.triggerId(erased.interners());
        // Immutable, reused every op: default chance roll (0.0) passes the 100% chance gate with no allocation.
        activation = Activation.builder(UUID.randomUUID(), 0, triggerId, 0L).build();
    }

    @Benchmark
    public boolean gateWalk() {
        return pipeline.evaluate(ability, activation).activated();
    }
}
