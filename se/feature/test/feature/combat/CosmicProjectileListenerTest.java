package feature.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

class CosmicProjectileListenerTest {

    @Test
    void hellfireUsesTheExactDuelsRadiusAndDamageScaling() {
        assertEquals(10, CosmicProjectileListener.hellfireRadius(5, "world"));
        assertEquals(5, CosmicProjectileListener.hellfireRadius(5, "world_duels2"));
        assertEquals(6.0, CosmicProjectileListener.hellfireDelayedDamage(5, "world"));
        assertEquals(4.0, CosmicProjectileListener.hellfireDelayedDamage(5, "world_duels2"));
    }

    @Test
    void bidirectionalPullUsesSourceBoundsAndNearHorizontalLift() {
        Vector minimum = CosmicProjectileListener.bidirectionalPull(new Vector(1.0, 0.001, 0.0), 1.0, 1);
        assertEquals(-1.0, minimum.getX(), 1.0E-12);
        assertEquals(-0.001 / 1.75 * 7.5, minimum.getY(), 1.0E-12);

        Vector capped = CosmicProjectileListener.bidirectionalPull(new Vector(1.0, 0.5, 0.0), 10_000.0, 5);
        assertEquals(-8.5, capped.getX(), 1.0E-12);
        assertEquals(-0.5 * 8.5 / 1.75, capped.getY(), 1.0E-12);
    }
}
