package compile.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import testfx.Abilities;

/** Tests the world-blacklist gate, including the non-interned-world (-1) guard. */
class AbilityTest {

    private static Ability withWorldBlacklist(long worldBlacklist) {
        return Abilities.ability().triggerMask(0).level(0).chance(0.0).worldBlacklist(worldBlacklist).build();
    }

    @Test
    void blockedInWorldChecksTheInternedBit() {
        Ability blacklistsWorld3 = withWorldBlacklist(1L << 3);
        assertTrue(blacklistsWorld3.blockedInWorld(3));
        assertFalse(blacklistsWorld3.blockedInWorld(2));
    }

    @Test
    void aNonInternedWorldIsNeverBlocked() {
        // worldId -1 (named in no blacklist) must never match — guard against 1L<<-1
        // masking to bit 63 and wrongly matching an all-bits blacklist
        assertFalse(withWorldBlacklist(-1L).blockedInWorld(-1));
        assertFalse(withWorldBlacklist(0L).blockedInWorld(-1));
    }
}
