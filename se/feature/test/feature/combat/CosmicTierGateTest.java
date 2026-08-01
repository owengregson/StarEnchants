package feature.combat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.World;
import org.junit.jupiter.api.Test;

class CosmicTierGateTest {

    @Test
    void tierSixPlusIsDisabledOnlyInTheEnd() {
        assertTrue(CosmicTierGate.tierSixPlusEnabled(World.Environment.NORMAL));
        assertTrue(CosmicTierGate.tierSixPlusEnabled(World.Environment.NETHER));
        assertFalse(CosmicTierGate.tierSixPlusEnabled(World.Environment.THE_END));
    }
}
