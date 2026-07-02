package bench;

import compile.model.Affinity;
import compile.model.CompiledEffect;
import compile.model.CompiledSelector;
import compile.model.Interners;
import compile.stage.DefaultEraseStage;
import compile.stage.ErasedContent;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.selector.SelectorCtx;
import engine.selector.SelectorKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.SelectorSpec;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import platform.sched.SchedulerBackend;
import platform.sched.TaskHandle;
import schema.diag.Diagnostics;
import schema.spec.Args;
import testfx.Defs;

/**
 * Shared synthetic world for the hot-path benchmarks: one enchant ability that fires on {@code ATTACK} with
 * a no-op effect, lowered with testfx's {@link Defs} builders and run through the real erase stage — a Snapshot
 * built entirely in memory, no server. The no-op effect/selector keep the measurement on the engine's own walk
 * (gate sequence + dispatch bookkeeping), not on Bukkit entity work.
 */
final class BenchWorld {

    static final String TRIGGER = "ATTACK";

    private BenchWorld() {
    }

    /** Erase a single-ability synthetic snapshot; {@code abilities[0]} fires on {@link #TRIGGER} with the no-op effect. */
    static ErasedContent erased() {
        CompiledEffect noop = new CompiledEffect("NOOP", Args.empty(),
                new CompiledSelector("NOOP", Args.empty()), 0, Affinity.CONTEXT_LOCAL);
        return new DefaultEraseStage().erase(List.of(
                Defs.lowered().stableKey("enchants/bench").triggers(TRIGGER).chance(100.0)
                        .affinity(Affinity.CONTEXT_LOCAL).effects(noop).build()),
                new Diagnostics());
    }

    /** The interned id of {@link #TRIGGER} in {@code interners} — the trigger the benchmark activation fires on. */
    static int triggerId(Interners interners) {
        return interners.triggers().idOf(TRIGGER);
    }

    /** A no-op effect kind (no target slots, emits nothing) so effect execution measures only the engine overhead. */
    static final class NoopEffect implements EffectKind {
        private final EffectSpec spec = EffectSpec.of("NOOP").affinity(Affinity.CONTEXT_LOCAL).build();

        @Override
        public EffectSpec spec() {
            return spec;
        }

        @Override
        public void run(EffectCtx ctx, Sink sink) {
        }
    }

    /** A no-op selector kind: resolves to no entities, so the executor path does no Bukkit entity work. */
    static final class NoopSelector implements SelectorKind {
        private final SelectorSpec spec = SelectorSpec.of("NOOP").build();

        @Override
        public SelectorSpec spec() {
            return spec;
        }

        @Override
        public List<org.bukkit.entity.LivingEntity> resolve(SelectorCtx ctx) {
            return List.of();
        }
    }

    /** Inline scheduler: runs every task synchronously, so a bench needs no server thread. */
    static final class InlineScheduler implements SchedulerBackend {
        @Override
        public void onEntity(Entity entity, Runnable task) {
            task.run();
        }

        @Override
        public void onEntityLater(Entity entity, long delayTicks, Runnable task) {
            task.run();
        }

        @Override
        public TaskHandle repeatingEntity(Entity entity, long initialDelayTicks, long periodTicks, Runnable task) {
            return TaskHandle.CANCELLED;
        }

        @Override
        public void onRegion(Location location, Runnable task) {
            task.run();
        }

        @Override
        public void onRegionLater(Location location, long delayTicks, Runnable task) {
            task.run();
        }

        @Override
        public TaskHandle repeatingRegion(Location location, long initialDelayTicks, long periodTicks, Runnable task) {
            return TaskHandle.CANCELLED;
        }

        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onGlobalLater(long delayTicks, Runnable task) {
            task.run();
        }

        @Override
        public TaskHandle repeatingGlobal(long initialDelayTicks, long periodTicks, Runnable task) {
            return TaskHandle.CANCELLED;
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }
    }
}
