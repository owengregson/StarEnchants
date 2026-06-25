/**
 * The authored-input model — content as loaded, before compilation (docs/architecture.md §4.1).
 * {@link compile.def.AbilityDef} is the single uniform shape every source reads into. Kept separate from
 * the compiled {@link compile.model} so each stage stays a pure function from one fixed type to the next.
 */
package compile.def;
