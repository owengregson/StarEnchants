/**
 * The StarEnchants type system: one {@link com.starenchants.schema.spec.ParamSpec}
 * per DSL kind, used four ways.
 *
 * <p>Each effect/condition/selector/trigger declares a {@code ParamSpec} —
 * an ordered list of typed {@link com.starenchants.schema.spec.Param}s built from
 * the {@link com.starenchants.schema.spec.D} vocabulary
 * ({@code D.DOUBLE.min(0).max(100)}). That single declaration drives validation
 * (→ typed {@link com.starenchants.schema.spec.Args}), tab-completion, {@code /se
 * docs}, and migration, so the four can never drift (docs/architecture.md §1.2 D5,
 * §7, §10).
 */
package com.starenchants.schema.spec;
