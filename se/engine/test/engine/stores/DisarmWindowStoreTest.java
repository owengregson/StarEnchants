package engine.stores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** ADR-0071 DISARM_SHUFFLE: a one-shot armed window read without consuming, consumed once. */
class DisarmWindowStoreTest {

    private static final double EPS = 1e-9;
    private final DisarmWindowStore store = new DisarmWindowStore();

    @Test
    void armExpiresAtDeadline() {
        UUID u = UUID.randomUUID();
        store.arm(u, 100L, 80, 0.2);
        DisarmWindowStore.Arm a = store.armed(u, 179L);
        assertNotNull(a);
        assertEquals(0.2, a.malusFraction(), EPS);
        assertNull(store.armed(u, 180L), "the expiry tick counts as elapsed (half-open)");
    }

    @Test
    void armedDoesNotConsume() {
        UUID u = UUID.randomUUID();
        store.arm(u, 0L, 80, 0.2);
        assertNotNull(store.armed(u, 10L));
        assertNotNull(store.armed(u, 20L), "reading the window does not consume it");
    }

    @Test
    void consumeIsOneShot() {
        UUID u = UUID.randomUUID();
        store.arm(u, 0L, 80, 0.2);
        store.consume(u);
        assertNull(store.armed(u, 10L), "consume clears the one-shot window");
    }
}
