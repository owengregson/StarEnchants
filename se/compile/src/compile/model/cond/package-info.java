/**
 * The compiled condition IR: a {@link compile.model.cond.Cond} root over
 * {@link compile.model.cond.NumExpr}/{@link compile.model.cond.StrExpr} operands, evaluated against a
 * primitive {@code FactBuffer} with no hot-path string work (docs/architecture.md §3.2, §3.4).
 */
package compile.model.cond;
