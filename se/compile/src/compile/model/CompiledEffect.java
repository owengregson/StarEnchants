package compile.model;

import compile.model.cond.Cond;
import compile.model.cond.NumExpr;
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
 * @param eachCondition       the per-target filter this effect's resolved targets are each tested against
 *                            (ADR-0076's {@code each-if}, with {@code each-chance} desugared into it), or
 *                            {@code null} for the overwhelming majority that declare none. HOISTED out of
 *                            {@code args} at lower time, exactly as {@code cumulativeWaitTicks} is, so the
 *                            executor reads it as a field instead of a map lookup on every hit
 * @param eachCooldown        a per-selector-target window stamped in the cooldown store's existing per-victim
 *                            dimension ({@code each-cooldown}), in ticks; {@code null} = none. An expression
 *                            rather than an int only because every numeric argument may be one — the constant
 *                            the corpus actually authors lowers to a {@code Lit} and costs two instanceof checks
 */
public record CompiledEffect(
        String head,
        Args args,
        CompiledSelector target,
        int cumulativeWaitTicks,
        Affinity affinity,
        int kindId,
        Cond eachCondition,
        NumExpr eachCooldown) {

    /** Un-stamped effect ({@code kindId = -1}) — the compiler stamps a real id, tests use the head-fallback path. */
    public CompiledEffect(String head, Args args, CompiledSelector target, int cumulativeWaitTicks, Affinity affinity) {
        this(head, args, target, cumulativeWaitTicks, affinity, -1);
    }

    /** A stamped effect with no per-target filter — the shape every effect had before ADR-0076. */
    public CompiledEffect(String head, Args args, CompiledSelector target, int cumulativeWaitTicks,
                          Affinity affinity, int kindId) {
        this(head, args, target, cumulativeWaitTicks, affinity, kindId, null, null);
    }

    /** Whether this effect asks anything of its targets one at a time — the executor's single opt-in test. */
    public boolean hasPerTargetFilter() {
        return eachCondition != null || eachCooldown != null;
    }

    /** A copy of this effect with {@code args}/{@code target} replaced but the stamped {@code kindId} kept. */
    public CompiledEffect withArgs(Args args) {
        return new CompiledEffect(head, args, target, cumulativeWaitTicks, affinity, kindId,
                eachCondition, eachCooldown);
    }

    /** A copy with the target selector replaced (resolve rewrites its interned handle args). */
    public CompiledEffect withTarget(CompiledSelector target) {
        return new CompiledEffect(head, args, target, cumulativeWaitTicks, affinity, kindId,
                eachCondition, eachCooldown);
    }
}
