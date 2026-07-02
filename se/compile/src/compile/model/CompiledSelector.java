package compile.model;

import schema.spec.Args;

/**
 * A target selector resolved at compile time to its kind name plus typed {@link Args}
 * (e.g. {@code @Aoe{r=4}} &rarr; head {@code AOE}, args {@code {r=4.0}}); the engine binds {@code head}
 * to a {@code SelectorKind} at snapshot load so the hot path never parses a selector
 * (docs/architecture.md §3.5, §7).
 *
 * @param kindId dense selector-kind id stamped at compile against the registry build (ADR-0039); {@code -1}
 *               for the un-stamped {@link #SELF} sentinel and hand-built test selectors, which fall back to a
 *               head lookup
 */
public record CompiledSelector(String head, Args args, int kindId) {

    /** The implicit self-target used when an effect line names no selector; stamped with a real id at lowering. */
    public static final CompiledSelector SELF = new CompiledSelector("SELF", Args.empty(), -1);

    /** Un-stamped selector ({@code kindId = -1}) — the compiler stamps a real id, tests use the head-fallback path. */
    public CompiledSelector(String head, Args args) {
        this(head, args, -1);
    }

    /** A copy of this selector stamped with its dense {@code kindId}. */
    public CompiledSelector withKindId(int kindId) {
        return new CompiledSelector(head, args, kindId);
    }
}
