package compile.model;

import schema.spec.Args;

/**
 * A flyweight compiled effect: canonical {@code head}, immutable typed {@link Args}, target
 * {@link CompiledSelector}, and cumulative tick delay (docs/architecture.md §3.2). No string survives to
 * runtime — the engine binds {@code head} to a shared stateless {@code EffectKind} at snapshot load and
 * reads {@code args} by name with no hot-path parsing.
 *
 * @param cumulativeWaitTicks ticks of {@code WAIT} accumulated before this effect in
 *                            its ability's effect list (fixes a Cosmic Enchants-style WAIT-overwrite bug, §3.6)
 */
public record CompiledEffect(
        String head,
        Args args,
        CompiledSelector target,
        int cumulativeWaitTicks,
        Affinity affinity) {
}
