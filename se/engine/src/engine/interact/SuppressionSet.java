package engine.interact;

import java.util.BitSet;

/**
 * The suppression arbiter: a per-activation set of interned ids that have been silenced
 * by a {@code DISABLE_ENCHANT}/{@code DISABLE_GROUP}/{@code DISABLE_TYPE} effect
 * (docs/architecture.md §6.2). Gate 5 of the pipeline is a single membership test —
 * {@link #contains}{@code (ability.suppressKey)} — so suppression is O(1) with no string
 * compares (the case-sensitivity divergence of the originals is gone: keys are
 * case-folded to interned ids at compile time).
 *
 * <p>Backed by a {@link BitSet} because interned ids are dense small integers, so a
 * membership test is a single word-and-bit lookup. This is per-activation scratch owned
 * by the firing thread — not thread-safe by design; reuse via {@link #clear}.
 *
 * <p><strong>Role-correctness</strong> (§6.2) is the pipeline's concern, not this set's:
 * {@code DISABLE_ENCHANT} keys the <em>defender</em> and {@code DISABLE_GROUP} keys the
 * <em>activator</em>, so an activation carries two of these sets (one per role) and each
 * ability checks the set matching the role its {@code suppressKey} was lowered against.
 */
public final class SuppressionSet {

    private final BitSet ids = new BitSet();

    /** Silence an interned id. Negative ids (meaning "no key") are ignored. */
    public void add(int id) {
        if (id >= 0) {
            ids.set(id);
        }
    }

    /** @return {@code true} if {@code id} has been suppressed this activation. */
    public boolean contains(int id) {
        return id >= 0 && ids.get(id);
    }

    /** @return {@code true} if nothing has been suppressed. */
    public boolean isEmpty() {
        return ids.isEmpty();
    }

    /** Reset for reuse on the next activation. */
    public void clear() {
        ids.clear();
    }
}
