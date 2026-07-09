package engine.stores;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The %combo% streak: consecutive same-target hits inside the window build; a victim switch or a lapse resets. */
class ComboStoreTest {

    private final UUID player = UUID.randomUUID();
    private final UUID victimA = UUID.randomUUID();
    private final UUID victimB = UUID.randomUUID();

    @Test
    void consecutiveSameVictimHitsInsideTheWindowBuildTheStreak() {
        ComboStore combo = new ComboStore(100L);
        assertEquals(1, combo.hit(player, victimA, 0L));
        assertEquals(2, combo.hit(player, victimA, 50L));
        assertEquals(3, combo.hit(player, victimA, 100L)); // gap 50 <= window
    }

    @Test
    void switchingVictimInsideTheWindowRestartsAtOne() {
        ComboStore combo = new ComboStore(100L);
        combo.hit(player, victimA, 0L);
        combo.hit(player, victimA, 10L); // streak 2 on A
        assertEquals(1, combo.hit(player, victimB, 20L)); // pre-charge on A cannot carry into B
        assertEquals(2, combo.hit(player, victimB, 30L));
    }

    @Test
    void aGapLongerThanTheWindowResetsToOne() {
        ComboStore combo = new ComboStore(100L);
        combo.hit(player, victimA, 0L);
        assertEquals(1, combo.hit(player, victimA, 201L)); // gap 201 > window
    }

    @Test
    void clearDropsTheStreak() {
        ComboStore combo = new ComboStore(100L);
        combo.hit(player, victimA, 0L);
        combo.clear(player);
        assertEquals(0, combo.current(player, 0L));
    }
}
