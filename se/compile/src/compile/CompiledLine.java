package compile;

import schema.diag.Source;
import schema.spec.Args;

/**
 * The validated result of compiling one authored effect/condition line: the
 * canonical head, its typed {@link Args}, and the {@link Source} it came from.
 *
 * <p>This is the boundary between authored text and the compiled world. Later
 * lowering stages turn a {@code CompiledLine} into a flyweight
 * {@code CompiledEffect} (a shared stateless kind + this typed-args record) and,
 * after source erasure, fold it into the snapshot's one {@code Ability[]}
 * (docs/architecture.md §3.2, §4.1). It carries no strings to parse at runtime.
 */
public record CompiledLine(String head, Args args, Source source) {
}
