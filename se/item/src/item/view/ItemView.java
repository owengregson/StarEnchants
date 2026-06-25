package item.view;

import item.codec.CombatState;

/**
 * Immutable cached decode of one item's combat state (§5.2): the hot path reads facts here, never
 * re-parses. Carries its decode generation so a pre-reload view is never read as current — the dense
 * ids a later layer resolves from these stable keys are valid only within that generation (§5.3).
 * Combat-only; identity/economy state is decoded separately, off the hot path (§5.1).
 */
public final class ItemView {

    private final int gen;
    private final CombatState combat;

    ItemView(int gen, CombatState combat) {
        this.gen = gen;
        this.combat = combat;
    }

    public int gen() {
        return gen;
    }

    public CombatState combat() {
        return combat;
    }

    public boolean isEmpty() {
        return combat.isEmpty();
    }
}
