package feature.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import engine.effect.kind.HeldEnchantLevels;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

final class CosmicHexUnfocusListenerTest {

    @Test
    void unfocusReadsTheCurrentHeldBowAtImpactInsteadOfLaunchState() {
        Player attacker = mock(Player.class);
        AtomicInteger currentLevel = new AtomicInteger(1);
        HeldEnchantLevels.resolver((player, key) -> currentLevel.get());
        try {
            assertEquals(1, CosmicHexUnfocusListener.unfocusLevelAtImpact(attacker));
            currentLevel.set(5);
            assertEquals(5, CosmicHexUnfocusListener.unfocusLevelAtImpact(attacker));
        } finally {
            HeldEnchantLevels.resolver(null);
        }
    }
}
