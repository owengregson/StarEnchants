package item.worn;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntUnaryOperator;

/**
 * The omni-aware multi-set resolver (docs/architecture.md §6.6, §5.5) — the single
 * most important correctness rule in the catalog. Given the armor pieces a player is
 * wearing, it computes the <em>set</em> of active armor sets, with omni pieces acting
 * as wildcards that count toward <em>every partially-worn</em> set at once. Several
 * sets can therefore be active simultaneously, which a single-{@code activeSetId} model
 * cannot represent (the originals' silent set-bonus drop).
 *
 * <p>An omni wildcard only completes a set the player already has at least one real
 * piece of — it cannot conjure a set from nothing. Pure and deterministic.
 */
public final class SetResolver {

    private SetResolver() {
    }

    /**
     * Resolve the active sets.
     *
     * @param wornSetIds     the interned set id of each worn non-omni armor piece
     *                       ({@code -1} for a piece that belongs to no set)
     * @param omniCount      how many omni wildcard pieces are worn
     * @param requiredPieces interned set id &rarr; pieces needed to complete that set
     *                       (a non-positive requirement makes the set uncompletable)
     * @return the {@link BitSet} of active set ids (a set bit per completed set)
     */
    public static BitSet activeSets(int[] wornSetIds, int omniCount, IntUnaryOperator requiredPieces) {
        // Tally real pieces per set; only sets with ≥1 real piece are eligible.
        Map<Integer, Integer> realPieces = new HashMap<>();
        for (int setId : wornSetIds) {
            if (setId >= 0) {
                realPieces.merge(setId, 1, Integer::sum);
            }
        }
        BitSet active = new BitSet();
        for (Map.Entry<Integer, Integer> entry : realPieces.entrySet()) {
            int setId = entry.getKey();
            int required = requiredPieces.applyAsInt(setId);
            // Omni wildcards count toward this partially-worn set (and every other one).
            int worn = entry.getValue() + Math.max(0, omniCount);
            if (required > 0 && worn >= required) {
                active.set(setId);
            }
        }
        return active;
    }
}
