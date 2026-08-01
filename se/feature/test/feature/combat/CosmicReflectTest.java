package feature.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class CosmicReflectTest {

    @Test
    void chancePreservesCosmicsIntegerDivisionLadder() {
        double[] expected = {0.02, 0.02, 0.03, 0.03, 0.03, 0.04, 0.04, 0.04, 0.05, 0.05};
        for (int level = 1; level <= expected.length; level++) {
            assertEquals(expected[level - 1], CosmicReflect.chance(level), 0.0000001);
        }
    }
}
