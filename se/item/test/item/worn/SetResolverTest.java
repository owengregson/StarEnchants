package item.worn;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.BitSet;
import java.util.function.IntUnaryOperator;
import org.junit.jupiter.api.Test;

/** The omni-aware multi-set resolution — the catalog's subtlest correctness rule (§5.5 #1, §6.6). */
class SetResolverTest {

    private static final int PHANTOM = 1;
    private static final int YETI = 2;

    /** Every set requires {@code req} pieces. */
    private static IntUnaryOperator uniform(int req) {
        return setId -> req;
    }

    @Test
    void fullRealSetIsActive() {
        BitSet active = SetResolver.activeSets(new int[]{PHANTOM, PHANTOM, PHANTOM}, 0, uniform(3));
        assertTrue(active.get(PHANTOM));
    }

    @Test
    void incompleteSetIsInactive() {
        BitSet active = SetResolver.activeSets(new int[]{PHANTOM, PHANTOM}, 0, uniform(3));
        assertFalse(active.get(PHANTOM));
    }

    @Test
    void omniCompletesAPartiallyWornSet() {
        BitSet active = SetResolver.activeSets(new int[]{PHANTOM, PHANTOM}, 1, uniform(3));
        assertTrue(active.get(PHANTOM)); // 2 real + 1 omni = 3
    }

    @Test
    void oneOmniCompletesTwoPartiallyWornSetsAtOnce() {
        // 2 phantom + 1 yeti + 1 omni (4 armor slots), each set requires 2.
        // The omni wildcard counts toward BOTH partially-worn sets simultaneously.
        BitSet active = SetResolver.activeSets(new int[]{PHANTOM, PHANTOM, YETI}, 1, uniform(2));
        assertTrue(active.get(PHANTOM)); // 2 + 1 = 3 ≥ 2
        assertTrue(active.get(YETI));    // 1 + 1 = 2 ≥ 2  ← a single-activeSetId model would drop this
    }

    @Test
    void omniAloneCannotConjureASet() {
        // No real pieces of any set → omni wildcards activate nothing.
        BitSet active = SetResolver.activeSets(new int[]{-1, -1}, 4, uniform(1));
        assertTrue(active.isEmpty());
    }

    @Test
    void nonSetPiecesAreIgnored() {
        BitSet active = SetResolver.activeSets(new int[]{PHANTOM, -1, -1, -1}, 0, uniform(1));
        assertTrue(active.get(PHANTOM)); // one real piece, requires 1
    }

    @Test
    void nonPositiveRequirementIsUncompletable() {
        BitSet active = SetResolver.activeSets(new int[]{PHANTOM, PHANTOM}, 5, uniform(0));
        assertTrue(active.isEmpty());
    }
}
