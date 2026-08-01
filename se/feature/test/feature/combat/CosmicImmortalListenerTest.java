package feature.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class CosmicImmortalListenerTest {

    @Test
    void preservesTheSourceSoulCostLadder() {
        assertEquals(4, CosmicImmortalListener.soulCost(1));
        assertEquals(3, CosmicImmortalListener.soulCost(2));
        assertEquals(2, CosmicImmortalListener.soulCost(3));
        assertEquals(1, CosmicImmortalListener.soulCost(4));
    }
}
