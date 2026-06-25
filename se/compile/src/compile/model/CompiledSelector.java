package compile.model;

import schema.spec.Args;

/**
 * A target selector resolved at compile time to its kind name plus typed {@link Args}
 * (e.g. {@code @Aoe{r=4}} &rarr; head {@code AOE}, args {@code {r=4.0}}); the engine binds {@code head}
 * to a {@code SelectorKind} at snapshot load so the hot path never parses a selector
 * (docs/architecture.md §3.5, §7).
 */
public record CompiledSelector(String head, Args args) {

    /** The implicit self-target used when an effect line names no selector. */
    public static final CompiledSelector SELF = new CompiledSelector("SELF", Args.empty());
}
