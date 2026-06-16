package platform.protect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ProtectionService} — the composed gate-2 protection check. Pure logic over the
 * {@link ProtectionProvider} SPI (only a Bukkit {@code Location} is mocked, never read); no live matrix
 * run is needed since the service adds no Bukkit/version surface (the gate-2 wiring is exercised
 * end-to-end by the live ProtectionSuite).
 */
class ProtectionServiceTest {

    private static final UUID ACTOR = UUID.randomUUID();

    @Test
    void allowsEverythingWhenNoProvidersAreRegistered() {
        ProtectionService service = new ProtectionService(List.of());
        assertEquals(0, service.providerCount());
        assertTrue(service.allows(ACTOR, loc()));
    }

    @Test
    void deniesWhenAnyProviderDenies() {
        ProtectionService service = new ProtectionService(
                List.of((a, w) -> true, (a, w) -> false, (a, w) -> true));
        assertFalse(service.allows(ACTOR, loc()));
    }

    @Test
    void allowsWhenEveryProviderAllows() {
        ProtectionService service = new ProtectionService(List.of((a, w) -> true, (a, w) -> true));
        assertTrue(service.allows(ACTOR, loc()));
    }

    @Test
    void shortCircuitsOnTheFirstDenyWithoutQueryingLaterProviders() {
        AtomicInteger laterCalls = new AtomicInteger();
        ProtectionProvider deny = (a, w) -> false;
        ProtectionProvider later = (a, w) -> {
            laterCalls.incrementAndGet();
            return true;
        };
        ProtectionService service = new ProtectionService(List.of(deny, later));
        assertFalse(service.allows(ACTOR, loc()));
        assertEquals(0, laterCalls.get(), "a provider after the first deny is never consulted");
    }

    @Test
    void aThrowingProviderIsTreatedAsAllowAndDoesNotPropagate() {
        ProtectionProvider boom = new ProtectionProvider() {
            @Override
            public boolean allows(UUID actor, Location where) {
                throw new RuntimeException("bridge blew up");
            }

            @Override
            public String name() {
                return "boom";
            }
        };
        ProtectionService service = new ProtectionService(List.of(boom));
        assertTrue(service.allows(ACTOR, loc())); // failure degrades to permissive
    }

    @Test
    void aNullActorOrLocationIsPermissive() {
        ProtectionService service = new ProtectionService(List.of((a, w) -> false));
        assertTrue(service.allows(null, loc()));
        assertTrue(service.allows(ACTOR, null));
    }

    private static Location loc() {
        return mock(Location.class); // never read — providers in these tests ignore it
    }
}
