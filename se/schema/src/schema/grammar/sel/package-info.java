/**
 * The selector sub-grammar: parsing a target-selector token {@code @Head{k=v,k=v}}
 * into an untyped {@link schema.grammar.sel.SelectorAst} (docs/architecture.md §2,
 * "selector grammar {@code @Sel{a=b}}"). Like the rest of {@code se-schema/grammar}
 * this produces structure only — typing and name resolution against the registered
 * {@code SelectorKind}s are se-compile's job.
 */
package schema.grammar.sel;
