package engine.sink;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Wall-clock combat tag: tag → in-combat within the window, clear ends it, null is never in combat. */
class CombatTagTest {

    private final UUID p = UUID.randomUUID();

    @AfterEach
    void clean() {
        CombatTag.clearAll();
    }

    @Test
    void taggedPlayerIsInCombatUntrackedIsNot() {
        assertFalse(CombatTag.inCombat(p)); // never tagged
        CombatTag.tag(p);
        assertTrue(CombatTag.inCombat(p));  // within the fresh window
    }

    @Test
    void clearEndsCombat() {
        CombatTag.tag(p);
        CombatTag.clear(p);
        assertFalse(CombatTag.inCombat(p));
    }

    @Test
    void nullIsNeverTaggedOrInCombat() {
        CombatTag.tag(null);
        assertFalse(CombatTag.inCombat(null));
    }
}
