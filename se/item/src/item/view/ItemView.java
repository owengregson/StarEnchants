package item.view;

import item.codec.CombatState;

/**
 * An immutable, cached decode of one item's combat-relevant state (docs/architecture.md §5.2).
 * Returned by {@link ItemViewCache#of}; the combat hot path reads facts from here and never
 * re-parses the item. It carries the snapshot generation it was decoded against so a view built
 * before a reload is never mistaken for a current one — the dense {@code Ability.id}s a later layer
 * resolves from these stable keys are valid only within their generation (§5.3).
 *
 * <p>Holds only the combat record today; identity/economy state (scrolls, dust, crates) is decoded
 * separately so it never lands on the combat hot path (§5.1).
 */
public final class ItemView {

    private final int gen;
    private final CombatState combat;

    ItemView(int gen, CombatState combat) {
        this.gen = gen;
        this.combat = combat;
    }

    /** The snapshot generation this view was decoded against (§5.2/§5.3). */
    public int gen() {
        return gen;
    }

    /** The decoded combat state — enchants + crystals by stable key; never {@code null}. */
    public CombatState combat() {
        return combat;
    }

    /** Whether the item carries no combat state at all — the common combat miss-path case. */
    public boolean isEmpty() {
        return combat.isEmpty();
    }
}
