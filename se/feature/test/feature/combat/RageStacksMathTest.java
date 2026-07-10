package feature.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The two pure rage-stack helpers: the absolute pitch ladder and the min(streak, level) clamp. */
class RageStacksMathTest {

    @Test
    void pitchIsTheAbsoluteDescendingLadderFlooredAt085() {
        assertEquals(1.35f, RageStacksService.pitch(1)); // 1.45 - 0.10*1
        assertEquals(1.25f, RageStacksService.pitch(2)); // a level-2 rage tops out here
        assertEquals(0.85f, RageStacksService.pitch(6)); // 1.45 - 0.60 = 0.85, the floor
        assertEquals(0.85f, RageStacksService.pitch(7)); // beyond 6 stays at the 0.85 floor
        assertEquals(1.45f, RageStacksService.pitch(0)); // the top of the ladder
    }

    @Test
    void clampIsTheMinOfStreakAndLevel() {
        assertEquals(3, RageStacksService.clamp(3, 5)); // streak below the level → the streak
        assertEquals(2, RageStacksService.clamp(9, 2)); // streak above the level → capped at the level (max stacks = level)
        assertEquals(4, RageStacksService.clamp(4, 4)); // equal
    }
}
