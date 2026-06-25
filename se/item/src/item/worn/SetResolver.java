package item.worn;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntUnaryOperator;

/**
 * Omni-aware multi-set resolver (§6.6, §5.5): computes the <em>set</em> of active armor sets from
 * worn pieces, with omni pieces as wildcards counting toward every partially-worn set at once. Several
 * sets can be active at once — a single-{@code activeSetId} model cannot represent that. An omni
 * wildcard only completes a set the player already has a real piece of; it cannot conjure one from
 * nothing. Pure and deterministic.
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
        // Only sets with ≥1 real piece are eligible (omni alone cannot complete a set).
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
            // Omni wildcards count toward this set and every other partially-worn one.
            int worn = entry.getValue() + Math.max(0, omniCount);
            if (required > 0 && worn >= required) {
                active.set(setId);
            }
        }
        return active;
    }
}
