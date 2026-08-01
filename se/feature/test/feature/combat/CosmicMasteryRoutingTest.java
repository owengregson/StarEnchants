package feature.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.DoubleSupplier;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

final class CosmicMasteryRoutingTest {

    private final Player source = mock(Player.class);
    private final Player target = mock(Player.class);

    @Test
    void dragonSlayerUsesTheExactCodeSideChances() {
        assertEquals(0.1001, CosmicMasteryRouting.reflectChance(10), 0.0000001);
        assertEquals(0.2599, CosmicMasteryRouting.negateChance(10), 0.0000001);
    }

    @Test
    void reflectSwapsTheMasteryRolesAtTheInclusiveBoundary() {
        CosmicMasteryRouting.Route route = CosmicMasteryRouting.routeForReflectLevel(
                source, target, 4, 10, values(0.1001));

        assertFalse(route.blocked());
        assertSame(target, route.source());
        assertSame(source, route.target());
    }

    @Test
    void negateRollOnlyRunsAfterReflectMisses() {
        CosmicMasteryRouting.Route route = CosmicMasteryRouting.routeForReflectLevel(
                source, target, 4, 10, values(0.1002, 0.2599));

        assertTrue(route.blocked());
        assertSame(source, route.source());
        assertSame(target, route.target());
    }

    @Test
    void failedReflectAndNegateKeepTheOriginalRoles() {
        CosmicMasteryRouting.Route route = CosmicMasteryRouting.routeForReflectLevel(
                source, target, 4, 10, values(0.1002, 0.2600));

        assertFalse(route.blocked());
        assertSame(source, route.source());
        assertSame(target, route.target());
    }

    @Test
    void lowerReflectLevelCannotReflectOrNegate() {
        CosmicMasteryRouting.Route route = CosmicMasteryRouting.routeForReflectLevel(
                source, target, 11, 10, () -> {
                    throw new AssertionError("random must not be consumed");
                });

        assertFalse(route.blocked());
        assertSame(source, route.source());
        assertSame(target, route.target());
    }

    @Test
    void chainLifestealUsesTheIntendedCeilBasedTargetCap() {
        assertEquals(2, CosmicMasteryListener.chainTargetCap(1));
        assertEquals(2, CosmicMasteryListener.chainTargetCap(2));
        assertEquals(3, CosmicMasteryListener.chainTargetCap(3));
        assertEquals(3, CosmicMasteryListener.chainTargetCap(4));
        assertEquals(4, CosmicMasteryListener.chainTargetCap(5));
    }

    @Test
    void mortalCoilRestoresTheExactWornHealthBoostAmplifier() {
        assertEquals(-1, CosmicMasteryListener.wornHealthBoostAmplifier(0, 0));
        assertEquals(0, CosmicMasteryListener.wornHealthBoostAmplifier(1, 0));
        assertEquals(2, CosmicMasteryListener.wornHealthBoostAmplifier(3, 0));
        assertEquals(3, CosmicMasteryListener.wornHealthBoostAmplifier(0, 1));
        assertEquals(4, CosmicMasteryListener.wornHealthBoostAmplifier(0, 2));
        assertEquals(5, CosmicMasteryListener.wornHealthBoostAmplifier(0, 3));
        assertEquals(5, CosmicMasteryListener.wornHealthBoostAmplifier(3, 3),
                "Godly Overload III must win over ordinary Overload III");
    }

    private static DoubleSupplier values(double... values) {
        AtomicInteger index = new AtomicInteger();
        return () -> values[index.getAndIncrement()];
    }
}
