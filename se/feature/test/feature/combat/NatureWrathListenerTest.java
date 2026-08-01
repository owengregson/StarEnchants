package feature.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class NatureWrathListenerTest {

    @Test
    void potionEffectsAlwaysUseTheFullSourceDuration() {
        int[] expected = {160, 180, 200, 220};
        for (int level = 1; level <= 4; level++) {
            assertEquals(expected[level - 1], NatureWrathListener.potionDuration(level));
        }
    }

    @Test
    void normalReleaseMatchesTheFullDuration() {
        int[] expected = {160, 180, 200, 220};
        for (int level = 1; level <= 4; level++) {
            assertEquals(expected[level - 1], NatureWrathListener.releaseDuration(level, false, 1.0));
        }
    }

    @Test
    void kothReleaseUsesTheSeparateFiveTickMultiplier() {
        int[] expected = {40, 45, 50, 55};
        for (int level = 1; level <= 4; level++) {
            assertEquals(expected[level - 1], NatureWrathListener.releaseDuration(level, true, 1.0));
        }
    }

    @Test
    void gaiaPetHalvesOnlyTheReleaseWindow() {
        int[] expectedNormal = {80, 90, 100, 110};
        int[] expectedKoth = {20, 22, 25, 27};
        for (int level = 1; level <= 4; level++) {
            assertEquals(expectedNormal[level - 1],
                    NatureWrathListener.releaseDuration(level, false, 0.5));
            assertEquals(expectedKoth[level - 1],
                    NatureWrathListener.releaseDuration(level, true, 0.5));
        }
    }
}
