package compile.model;

/**
 * Which content source an {@link Ability} was erased from — a <em>tag</em>, never a subtype, so
 * per-source special-casing is structurally impossible (docs/architecture.md §4.1, §12.2). Used only for
 * diagnostics, the {@link SourceMap}, and the rare source-aware gate.
 */
public enum SourceKind {

    /** A custom enchantment level (the merged Cosmic Enchants-style enchant channel). */
    ENCHANT,

    SET,

    WEAPON,

    /** A crystal effect (a first-class source; crystals are a list, §6.5). */
    CRYSTAL,

    /** A heroic flat-stat source. */
    HEROIC,

    /** A right-click use-item's ability (its own content family, {@code content/use-items/}), fired by the USE trigger. */
    USE_ITEM,

    /** A pet bracket's ability ({@code content/pets/}, ADR-0052) — hotbar-resolved, level-bracket-selected. */
    PET,

    /** A mask ability ({@code content/masks/}, ADR-0053) — resolved from a masked helmet's stored key, helmets-only. */
    MASK,

    /** A weapon-reforge ability ({@code content/reforges/}, ADR-0070) — resolved from a reforged weapon's stored
     *  key while that weapon is HELD; the active fires on the implicit USE trigger (shift+right-click). */
    REFORGE
}
