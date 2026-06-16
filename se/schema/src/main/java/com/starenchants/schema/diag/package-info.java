/**
 * Diagnostics: the typed-language reporting layer.
 *
 * <p>{@link com.starenchants.schema.diag.Source} positions, severity-tagged
 * {@link com.starenchants.schema.diag.Diagnostic}s, and the
 * {@link com.starenchants.schema.diag.Diagnostics} collector are carried from the
 * config loader through every compile stage, so a malformed line is a precise
 * {@code file:line:col} report at load — never a fail-at-fire-time exception
 * mid-combat (docs/architecture.md §1.2 D5, §10).
 */
package com.starenchants.schema.diag;
