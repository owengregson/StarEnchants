package engine.effect.kind;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The {@code spread-y} sentinel fold: a negative vertical offset (the {@code -1} default) reuses the horizontal spread. */
class ParticleSpreadTest {

    @Test
    void negativeSpreadYFoldsOntoTheHorizontalSpread() {
        assertEquals(0.4, ParticleEffect.spreadY(0.4, -1.0)); // the -1 default sentinel → use spread
        assertEquals(0.0, ParticleEffect.spreadY(0.0, -1.0)); // spread 0 + sentinel → a flat point burst
    }

    @Test
    void nonNegativeSpreadYIsUsedVerbatim() {
        assertEquals(2.0, ParticleEffect.spreadY(0.4, 2.0)); // explicit vertical offset
        assertEquals(0.0, ParticleEffect.spreadY(1.5, 0.0)); // an explicit 0 is NOT the sentinel — a flat vertical
    }
}
