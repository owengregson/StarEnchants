package compile.cond;

/**
 * A condition variable resolved to its type and dense {@code FactBuffer} slot (docs/architecture.md §3.4).
 * Compiler bakes {@code slot} into the IR, engine populates the same slot at activation — they agree by
 * construction because both read one shared variable vocabulary.
 *
 * @param slot index within the {@code FactBuffer} slot space for {@code kind}
 */
public record VarBinding(VarKind kind, int slot) {
}
