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

    @Test
    void currentReadsTheLiveStreakWithoutRegisteringAHitAndLapsesWithTheWindow() {
        ComboStore combo = new ComboStore(100L);
        combo.hit(player, victimA, 0L);
        combo.hit(player, victimA, 10L); // streak 2, last hit at tick 10
        assertEquals(2, combo.current(player, 100L)); // gap 90 <= window → still live, and NOT advanced
        assertEquals(2, combo.current(player, 100L)); // idempotent: a read never extends the streak
        assertEquals(0, combo.current(player, 111L)); // gap 101 > window → lapsed
        assertEquals(0, combo.current(UUID.randomUUID(), 0L)); // a player who never hit → 0
    }

    @Test
    void rollbackUndoesACancelledHitsAdvanceAndItsWindowRefresh() {
        ComboStore combo = new ComboStore(100L);
        combo.hit(player, victimA, 0L);
        combo.hit(player, victimA, 10L); // streak 2, last hit at tick 10
        Object mark = combo.mark(player);
        assertEquals(3, combo.hit(player, victimA, 90L)); // the walk sees the advanced streak…
        combo.rollback(player, mark);                     // …but the hit was dodged/inverted
        assertEquals(2, combo.current(player, 100L));     // count reverted
        assertEquals(0, combo.current(player, 115L));     // window runs from tick 10 again — the cancel did not refresh it
    }

    @Test
    void rollbackToNoPriorStreakRemovesTheEntry() {
        ComboStore combo = new ComboStore(100L);
        Object mark = combo.mark(player); // no streak yet
        combo.hit(player, victimA, 0L);
        combo.rollback(player, mark);
        assertEquals(0, combo.current(player, 0L)); // a first swing that was cancelled starts nothing
    }
}
