package compile.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests the world-blacklist gate, including the non-interned-world (-1) guard. */
class AbilityTest {

    private static Ability withWorldBlacklist(long worldBlacklist) {
        return new Ability(0, 0, SourceKind.ENCHANT, 0, 0, 0.0, 0, 0, worldBlacklist, null,
                new CompiledEffect[0], 0, Affinity.CONTEXT_LOCAL, -1, -1, -1, -1, 0);
    }

    @Test
    void blockedInWorldChecksTheInternedBit() {
        Ability blacklistsWorld3 = withWorldBlacklist(1L << 3);
        assertTrue(blacklistsWorld3.blockedInWorld(3));
        assertFalse(blacklistsWorld3.blockedInWorld(2));
    }

    @Test
    void aNonInternedWorldIsNeverBlocked() {
        // worldId -1 = a world named in no blacklist; must never be blocked, and must not let the
        // undefined 1L<<-1 (masked to bit 63) wrongly match an all-bits blacklist.
        assertFalse(withWorldBlacklist(-1L).blockedInWorld(-1));
        assertFalse(withWorldBlacklist(0L).blockedInWorld(-1));
    }
}
