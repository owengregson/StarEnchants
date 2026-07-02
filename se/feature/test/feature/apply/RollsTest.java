package feature.apply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

/** {@link Rolls} — the injected-Random span roll and percent gate the apply/mint economies share. */
final class RollsTest {

    /** A Random whose nextInt(bound) always returns a fixed value — lets a test pin the exact draw. */
    private static Random fixed(int value) {
        return new Random() {
            @Override
            public int nextInt(int bound) {
                return value;
            }
        };
    }

    /** A Random that fails if any draw is consumed — proves the empty-span path takes NO draw. */
    private static Random throwing() {
        return new Random() {
            @Override
            public int nextInt(int bound) {
                throw new AssertionError("should not draw");
            }
        };
    }

    @Test
    void betweenIsInclusive() {
        assertEquals(3, Rolls.between(fixed(0), 3, 7)); // draw 0 → min
        assertEquals(7, Rolls.between(fixed(4), 3, 7)); // draw span (bound-1) → max
    }

    @Test
    void betweenEmptySpanConsumesNoDraw() {
        assertEquals(5, Rolls.between(throwing(), 5, 5));
    }

    @Test
    void passesIsStrictlyLess() {
        assertFalse(Rolls.passes(fixed(0), 0));   // 0% never fires
        assertTrue(Rolls.passes(fixed(99), 100)); // 100% always fires
        assertTrue(Rolls.passes(fixed(29), 30));  // 29 < 30
        assertFalse(Rolls.passes(fixed(30), 30)); // 30 < 30 is false
    }
}
