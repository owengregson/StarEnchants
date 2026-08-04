package engine.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The decay ladder's arithmetic, hand-computed. The three properties that make it feel like rot rather than a
 * timed burn: it climbs to a ceiling, it lapses on its own clock, and it belongs to the VICTIM.
 */
class StackingDotsTest {

    private static final UUID VICTIM = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-0000000000c2");

    @AfterEach
    void drain() {
        StackingDots.clearAll();
    }

    @Test
    void stacksClimbOnePerPulseAndStopAtTheCap() {
        assertEquals(1, StackingDots.bump(VICTIM, 3, 60, 0));
        assertEquals(2, StackingDots.bump(VICTIM, 3, 60, 10));
        assertEquals(3, StackingDots.bump(VICTIM, 3, 60, 20));
        assertEquals(3, StackingDots.bump(VICTIM, 3, 60, 30), "the ceiling holds — 2 x cap is the ladder's top");
    }

    // Stepping off the ground PAUSES the ramp; the ladder only restarts once the window has actually lapsed.
    @Test
    void aLapsedWindowRestartsTheLadderButAPauseWithinItDoesNot() {
        StackingDots.bump(VICTIM, 6, 60, 0);
        StackingDots.bump(VICTIM, 6, 60, 10);
        assertEquals(2, StackingDots.stacks(VICTIM, 40), "still live 30 ticks into a 60-tick window");

        assertEquals(3, StackingDots.bump(VICTIM, 6, 60, 65), "back on the ground inside the window — resumed");

        assertEquals(0, StackingDots.stacks(VICTIM, 200), "the window lapsed, so the ladder is gone");
        assertEquals(1, StackingDots.bump(VICTIM, 6, 60, 200), "and re-entering starts the climb over");
    }

    // One ladder per victim, shared by every attacker: two overlapping fields must not double a gank's output.
    @Test
    void theLadderBelongsToTheVictimNotThePair() {
        StackingDots.bump(VICTIM, 6, 60, 0);
        StackingDots.bump(VICTIM, 6, 60, 0); // a second wearer's field, same victim, same tick
        assertEquals(2, StackingDots.stacks(VICTIM, 0));
        assertEquals(0, StackingDots.stacks(OTHER, 0), "and a bystander carries nothing");
    }

    @Test
    void clearDropsOneLadderOutright() {
        StackingDots.bump(VICTIM, 6, 60, 0);
        StackingDots.bump(OTHER, 6, 60, 0);
        StackingDots.clear(VICTIM);
        assertEquals(0, StackingDots.stacks(VICTIM, 0));
        assertEquals(1, StackingDots.stacks(OTHER, 0));
    }
}
