package engine.sink;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Per-owner hellfire zones: cylinder containment (xz, y-ignored), owner/world scoping, multi-zone, guards. */
class OwnerZonesTest {

    private final UUID owner = UUID.randomUUID();
    private final UUID worldId = UUID.randomUUID();

    @AfterEach
    void clean() {
        OwnerZones.clearAll();
    }

    /** A real Location in the test world at (x, z); the y is deliberately varied to prove the cylinder ignores it. */
    private Location at(double x, double y, double z) {
        World world = mock(World.class);
        lenient().when(world.getUID()).thenReturn(worldId);
        return new Location(world, x, y, z);
    }

    private Location inOtherWorld(double x, double z) {
        World world = mock(World.class);
        when(world.getUID()).thenReturn(UUID.randomUUID()); // a different world entirely
        return new Location(world, x, 0, z);
    }

    @Test
    void containsIsTrueOnlyInsideTheCylinderRegardlessOfHeight() {
        OwnerZones.mark(owner, worldId, 0, 0, 4.0, 60_000L);
        assertTrue(OwnerZones.contains(owner, at(3, 100, 0)));   // dist 3 < 4, any height
        assertTrue(OwnerZones.contains(owner, at(0, -40, 3.9))); // just inside, far below
        assertFalse(OwnerZones.contains(owner, at(5, 0, 0)));    // dist 5 > 4 → outside
    }

    @Test
    void aZoneIsScopedToItsOwnerAndWorld() {
        OwnerZones.mark(owner, worldId, 0, 0, 4.0, 60_000L);
        assertFalse(OwnerZones.contains(UUID.randomUUID(), at(1, 0, 1))); // a different owner sees nothing
        assertFalse(OwnerZones.contains(owner, inOtherWorld(1, 1)));      // same coords, wrong world
    }

    @Test
    void anOwnerCanHoldSeveralZonesAtOnce() {
        OwnerZones.mark(owner, worldId, 0, 0, 2.0, 60_000L);
        OwnerZones.mark(owner, worldId, 50, 0, 2.0, 60_000L); // a second, far-away zone
        assertTrue(OwnerZones.contains(owner, at(50, 0, 1)));  // inside the second zone, not the first
    }

    @Test
    void guardsRejectNoOpZonesAndClearForgetsThem() {
        OwnerZones.mark(null, worldId, 0, 0, 4.0, 60_000L);
        OwnerZones.mark(owner, null, 0, 0, 4.0, 60_000L);
        OwnerZones.mark(owner, worldId, 0, 0, 0.0, 60_000L); // zero radius
        OwnerZones.mark(owner, worldId, 0, 0, 4.0, 0L);      // zero duration
        assertFalse(OwnerZones.contains(owner, at(0, 0, 0)));

        OwnerZones.mark(owner, worldId, 0, 0, 4.0, 60_000L);
        OwnerZones.clear(owner);
        assertFalse(OwnerZones.contains(owner, at(0, 0, 0)));
    }
}
