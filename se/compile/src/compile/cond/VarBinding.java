package compile.cond;

/**
 * The resolution of a condition variable to its type and its dense {@code FactBuffer}
 * slot (docs/architecture.md §3.4). The compiler bakes the {@code slot} into the
 * compiled condition IR; the engine populates the same slot at activation, so the two
 * agree by construction because both read one shared variable vocabulary.
 *
 * @param kind the variable's value type
 * @param slot its index within the {@code FactBuffer} slot space for {@code kind}
 */
public record VarBinding(VarKind kind, int slot) {
}
