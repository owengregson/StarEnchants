package feature.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class CosmicSelfDestructListenerTest {

    @Test
    void preservesCosmicCountsAndFuses() {
        assertEquals(2, CosmicSelfDestructListener.countForLevel(1));
        assertEquals(5, CosmicSelfDestructListener.countForLevel(2));
        assertEquals(7, CosmicSelfDestructListener.countForLevel(3));

        assertEquals(100, CosmicSelfDestructListener.fuseForLevel(1));
        assertEquals(80, CosmicSelfDestructListener.fuseForLevel(2));
        assertEquals(60, CosmicSelfDestructListener.fuseForLevel(3));
    }
}
