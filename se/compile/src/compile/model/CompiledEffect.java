package compile.model;

import schema.spec.Args;

/**
 * A flyweight compiled effect (docs/architecture.md §3.2). No string survives to runtime — the engine
 * binds {@code head} to a shared stateless {@code EffectKind} at snapshot load and reads {@code args}
 * by name with no hot-path parsing.
 *
 * @param cumulativeWaitTicks ticks of {@code WAIT} accumulated before this effect in
 *                            its ability's effect list (fixes a Cosmic Enchants-style WAIT-overwrite bug, §3.6)
 * @param kindId              dense effect-kind id stamped at compile against the registry build (ADR-0039), so
 *                            the executor dispatches by array index, never a per-execution head lookup; {@code -1}
 *                            for hand-built test effects, which fall back to a head lookup
 */
public record CompiledEffect(
        String head,
        Args args,
        CompiledSelector target,
        int cumulativeWaitTicks,
        Affinity affinity,
        int kindId) {

    /** Un-stamped effect ({@code kindId = -1}) — the compiler stamps a real id, tests use the head-fallback path. */
    public CompiledEffect(String head, Args args, CompiledSelector target, int cumulativeWaitTicks, Affinity affinity) {
        this(head, args, target, cumulativeWaitTicks, affinity, -1);
    }

    /** A copy of this effect with {@code args}/{@code target} replaced but the stamped {@code kindId} kept. */
    public CompiledEffect withArgs(Args args) {
        return new CompiledEffect(head, args, target, cumulativeWaitTicks, affinity, kindId);
    }

    /** A copy with the target selector replaced (resolve rewrites its interned handle args). */
    public CompiledEffect withTarget(CompiledSelector target) {
        return new CompiledEffect(head, args, target, cumulativeWaitTicks, affinity, kindId);
    }
}
