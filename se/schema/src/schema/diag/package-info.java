/**
 * Diagnostics: the typed-language reporting layer.
 *
 * <p>{@link schema.diag.Source} positions, severity-tagged
 * {@link schema.diag.Diagnostic}s, and the
 * {@link schema.diag.Diagnostics} collector are carried from the
 * config loader through every compile stage, so a malformed line is a precise
 * {@code file:line:col} report at load — never a fail-at-fire-time exception
 * mid-combat (docs/architecture.md §1.2 D5, §10).
 */
package schema.diag;
