/**
 * The authored-input model — content as loaded, before it is compiled
 * (docs/architecture.md §4.1). {@link compile.def.AbilityDef} is the single uniform
 * shape every one of the five sources is read into (source erasure starts here);
 * the YAML loader produces these and the lower/erase stages consume them.
 *
 * <p>Pure and authored-text-shaped: effect lines are lexed but not yet validated,
 * names are not yet interned. Keeping the input model separate from the compiled
 * {@link compile.model} world keeps each compile stage a pure function from one
 * fixed type to the next.
 */
package compile.def;
