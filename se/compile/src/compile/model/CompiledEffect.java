package compile.model;

import schema.spec.Args;

/**
 * A flyweight compiled effect: the kind's canonical {@code head}, its immutable
 * typed {@link Args}, the {@link CompiledSelector} naming its targets, and the
 * cumulative tick delay before it runs (docs/architecture.md §3.2). No string
 * survives to runtime — the engine resolves {@code head} to a shared stateless
 * {@code EffectKind} instance at snapshot load and the kind reads {@code args} by
 * name with no parsing on the hot path.
 *
 * @param head                the kind's canonical (upper-case) head, e.g. {@code DAMAGE}
 * @param args                already-parsed, range-checked arguments
 * @param target              the resolved target selector ({@link CompiledSelector#SELF} if none)
 * @param cumulativeWaitTicks ticks of {@code WAIT} accumulated before this effect in
 *                            its ability's effect list (fixes a Cosmic Enchants-style WAIT-overwrite bug, §3.6)
 * @param affinity            the kind's declared dispatch affinity (§3.6)
 */
public record CompiledEffect(
        String head,
        Args args,
        CompiledSelector target,
        int cumulativeWaitTicks,
        Affinity affinity) {
}
