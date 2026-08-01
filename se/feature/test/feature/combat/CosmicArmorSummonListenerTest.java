package feature.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class CosmicArmorSummonListenerTest {

    @Test
    void spiritsUsesTheIntendedGuardiansStyleChanceCurve() {
        assertEquals(0.06, CosmicArmorSummonListener.spiritChanceForLevel(1), 0.0000001);
        assertEquals(0.11, CosmicArmorSummonListener.spiritChanceForLevel(2), 0.0000001);
        assertEquals(0.16, CosmicArmorSummonListener.spiritChanceForLevel(3), 0.0000001);
        assertEquals(0.20, CosmicArmorSummonListener.spiritChanceForLevel(4), 0.0000001);
        assertEquals(0.20, CosmicArmorSummonListener.spiritChanceForLevel(10), 0.0000001);
    }

    @Test
    void spiritsPreservesTheSourceHealLadder() {
        int[] intervals = {84, 78, 72, 66, 60, 54, 48, 42, 36, 30};
        int[] amounts = {1, 1, 1, 1, 1, 2, 2, 2, 2, 2};
        int[] targets = {1, 1, 1, 1, 1, 1, 2, 2, 2, 2};
        for (int level = 1; level <= 10; level++) {
            assertEquals(intervals[level - 1], CosmicArmorSummonListener.spiritHealInterval(level));
            assertEquals(amounts[level - 1], CosmicArmorSummonListener.spiritHealAmount(level));
            assertEquals(targets[level - 1], CosmicArmorSummonListener.spiritMaxHealTargets(level));
        }
    }

    @Test
    void undeadRuseUsesTheIntendedSpawnCountVisibilityScaling() {
        assertEquals(40, CosmicArmorSummonListener.undeadInvisibilityTicks(1));
        assertEquals(80, CosmicArmorSummonListener.undeadInvisibilityTicks(3));
        assertEquals(120, CosmicArmorSummonListener.undeadInvisibilityTicks(5));
    }
}
