/**
 * The authored-input model — content as loaded, before compilation (docs/architecture.md §4.1).
 * {@link compile.def.AbilityDef} is the single uniform shape every source reads into (source erasure
 * starts here). Authored-text-shaped (effect lines lexed not validated, names not interned); keeping it
 * separate from compiled {@link compile.model} keeps each stage a pure function from one fixed type to the next.
 */
package compile.def;
