/**
 * The compiled condition IR: the typed, slot-resolved tree
 * ({@link compile.model.cond.Cond} root over {@link compile.model.cond.NumExpr} /
 * {@link compile.model.cond.StrExpr} operands) the runtime evaluates against a
 * primitive {@code FactBuffer} (docs/architecture.md §3.2, §3.4). Pure data: every
 * variable is a dense slot and every literal is pre-parsed, so the hot path never
 * touches a string.
 */
package compile.model.cond;
