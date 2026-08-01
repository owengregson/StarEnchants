package feature.pet;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class CosmicWorldDestroyerTest {

    @Test
    void cageUsesTheCodeSideFiveSecondLifetimeNotTheThreeSecondLore() {
        assertEquals(100, CosmicWorldDestroyer.CAGE_TICKS);
    }
}
