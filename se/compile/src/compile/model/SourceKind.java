package compile.model;

/**
 * Which of the five content sources an {@link Ability} was erased from. This is a
 * <em>tag</em> on an otherwise-identical struct, never a subtype: all five sources
 * lower to one {@code Ability} record, so the hot path handles them uniformly and
 * per-source special-casing is structurally impossible (docs/architecture.md §4.1,
 * §12.2). The tag exists only for diagnostics, the {@link SourceMap}, and the rare
 * source-aware gate.
 */
public enum SourceKind {

    /** A custom enchantment level (the merged EE/EA enchant channel). */
    ENCHANT,

    /** An armor-set bonus. */
    SET,

    /** A weapon bonus. */
    WEAPON,

    /** A crystal effect (a first-class source; crystals are a list, §6.5). */
    CRYSTAL,

    /** A heroic flat-stat source. */
    HEROIC
}
