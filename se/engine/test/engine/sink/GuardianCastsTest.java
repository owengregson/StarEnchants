package engine.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class GuardianCastsTest {

    @AfterEach
    void clear() {
        GuardianCasts.clearAll();
    }

    @Test
    void onlyCosmicsThreeSourceMetadataFamiliesAreRotAndDecayPurgeable() {
        UUID owner = UUID.randomUUID();
        for (GuardianCasts.Kind kind : GuardianCasts.Kind.values()) {
            UUID entity = UUID.randomUUID();
            GuardianCasts.bind(entity, owner, kind);
            assertEquals(kind.rotAndDecayPurgeable(), GuardianCasts.rotAndDecayPurgeable(entity));
        }
    }

    @Test
    void forgettingASummonRunsItsCleanupExactlyOnce() {
        UUID entity = UUID.randomUUID();
        AtomicInteger cleanups = new AtomicInteger();
        GuardianCasts.bind(entity, UUID.randomUUID(), GuardianCasts.Kind.ANCESTRAL,
                cleanups::incrementAndGet);

        GuardianCasts.forget(entity);
        GuardianCasts.forget(entity);

        assertEquals(1, cleanups.get());
        assertNull(GuardianCasts.owner(entity));
        assertFalse(GuardianCasts.rotAndDecayPurgeable(entity));
    }

    @Test
    void ownershipConversionPreservesSourceFamilyAndCleanup() {
        UUID entity = UUID.randomUUID();
        UUID firstOwner = UUID.randomUUID();
        UUID convertedOwner = UUID.randomUUID();
        AtomicInteger cleanups = new AtomicInteger();
        GuardianCasts.bind(entity, firstOwner, GuardianCasts.Kind.GUARDIAN, cleanups::incrementAndGet);

        GuardianCasts.bind(entity, convertedOwner);

        assertEquals(convertedOwner, GuardianCasts.owner(entity));
        assertEquals(GuardianCasts.Kind.GUARDIAN, GuardianCasts.kind(entity));
        assertTrue(GuardianCasts.rotAndDecayPurgeable(entity));
        GuardianCasts.forget(entity);
        assertEquals(1, cleanups.get());
    }

    @Test
    void genericOwnedSpawnsRemainUnpurgeable() {
        UUID entity = UUID.randomUUID();
        GuardianCasts.bind(entity, UUID.randomUUID());

        assertEquals(GuardianCasts.Kind.OTHER, GuardianCasts.kind(entity));
        assertFalse(GuardianCasts.rotAndDecayPurgeable(entity));
    }
}
