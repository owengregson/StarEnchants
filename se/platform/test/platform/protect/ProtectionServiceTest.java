package platform.protect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ProtectionService} — the composed gate-2 protection check. Pure logic over the
 * {@link ProtectionProvider} SPI (only the Bukkit {@code Player}/{@code Location} are mocked); no live
 * matrix run is needed since the service adds no Bukkit/version surface (the gate-2 wiring is exercised
 * end-to-end by the live ProtectionSuite, and the reflective WorldGuard bridge is verified only for
 * graceful absence — there is no WorldGuard on the test matrix).
 */
class ProtectionServiceTest {

    private static final UUID ACTOR = UUID.randomUUID();
    private static final UUID WORLD = UUID.randomUUID();

    @Test
    void allowsEverythingWhenNoProvidersAreRegistered() {
        ProtectionService service = new ProtectionService(List.of(), () -> 0L);
        assertEquals(0, service.providerCount());
        assertTrue(service.allows(player(), location(10, 64, 10)));
    }

    @Test
    void deniesWhenAnyProviderDenies() {
        ProtectionService service = new ProtectionService(
                List.of((a, w) -> true, (a, w) -> false, (a, w) -> true), () -> 0L);
        assertFalse(service.allows(player(), location(0, 0, 0)));
    }

    @Test
    void allowsWhenEveryProviderAllows() {
        ProtectionService service = new ProtectionService(
                List.of((a, w) -> true, (a, w) -> true), () -> 0L);
        assertTrue(service.allows(player(), location(0, 0, 0)));
    }

    @Test
    void aThrowingProviderIsTreatedAsAllowAndDoesNotPropagate() {
        ProtectionProvider boom = new ProtectionProvider() {
            @Override
            public boolean allows(Player actor, Location where) {
                throw new RuntimeException("bridge blew up");
            }

            @Override
            public String name() {
                return "boom";
            }
        };
        ProtectionService service = new ProtectionService(List.of(boom), () -> 0L);
        assertTrue(service.allows(player(), location(0, 0, 0))); // failure degrades to permissive
    }

    @Test
    void cachesWithinTheSameTickThenReQueriesWhenTheTickAdvances() {
        AtomicInteger calls = new AtomicInteger();
        AtomicLong tick = new AtomicLong(5L);
        ProtectionProvider counting = (a, w) -> {
            calls.incrementAndGet();
            return true;
        };
        ProtectionService service = new ProtectionService(List.of(counting), tick::get);
        Player player = player();
        Location at = location(3, 70, 3);

        service.allows(player, at);
        service.allows(player, at);
        service.allows(player, at);
        assertEquals(1, calls.get(), "same tick + same block ⇒ one provider query");

        tick.incrementAndGet(); // next tick invalidates the cached decision
        service.allows(player, at);
        assertEquals(2, calls.get(), "tick advanced ⇒ re-query");
    }

    @Test
    void reQueriesWhenTheBlockChangesWithinTheSameTick() {
        AtomicInteger calls = new AtomicInteger();
        ProtectionProvider counting = (a, w) -> {
            calls.incrementAndGet();
            return true;
        };
        ProtectionService service = new ProtectionService(List.of(counting), () -> 9L);
        Player player = player();

        service.allows(player, location(0, 64, 0));
        service.allows(player, location(0, 64, 1)); // different block, same tick ⇒ re-query
        assertEquals(2, calls.get());
    }

    private static Player player() {
        Player player = mock(Player.class);
        lenient().when(player.getUniqueId()).thenReturn(ACTOR);
        return player;
    }

    private static Location location(int x, int y, int z) {
        World world = mock(World.class);
        lenient().when(world.getUID()).thenReturn(WORLD);
        Location loc = mock(Location.class);
        when(loc.getWorld()).thenReturn(world);
        lenient().when(loc.getBlockX()).thenReturn(x);
        lenient().when(loc.getBlockY()).thenReturn(y);
        lenient().when(loc.getBlockZ()).thenReturn(z);
        return loc;
    }
}
