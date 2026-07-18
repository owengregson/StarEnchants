package feature.combat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import engine.stores.EngineStores;
import engine.stores.HitTempoStore;
import java.util.UUID;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The reforge strike relay (ADR-0071), the {@link ParkFlushRelay} identity contract: a mark is routed to
 * exactly the listener whose event AND stores aggregate produced it (the parallel dispatch+stores triples the
 * live tester stands up), and the builder accumulates the three independent concerns of one hit.
 */
class ReforgeStrikeRelayTest {

    @AfterEach
    void clearRelay() {
        ReforgeStrikeRelay.clear();
    }

    @Test
    void keyedByEventAndStoresIdentity() {
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        EngineStores stores = EngineStores.fresh();
        UUID attacker = UUID.randomUUID();
        UUID victim = UUID.randomUUID();
        ReforgeStrikeRelay.mark(event, stores, attacker, victim, ReforgeStrikeRelay.battery(null));

        ReforgeStrikeRelay.Pending consumed = ReforgeStrikeRelay.consume(event, stores);

        assertSame(event, consumed.event());
        assertSame(stores, consumed.stores());
        assertNull(ReforgeStrikeRelay.consume(event, stores), "consume is one-shot — a second read is empty");
    }

    @Test
    void foreignStoresMarkIgnored() {
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        EngineStores stores = EngineStores.fresh();
        EngineStores foreign = EngineStores.fresh();
        ReforgeStrikeRelay.mark(event, stores, UUID.randomUUID(), UUID.randomUUID(), ReforgeStrikeRelay.disarm(null));

        assertNull(ReforgeStrikeRelay.consume(event, foreign), "another aggregate never consumes this mark");
        assertTrue(ReforgeStrikeRelay.consume(event, stores).disarmStrike(), "the owning aggregate still can");
    }

    @Test
    void staleEventMarkIgnored() {
        EntityDamageByEntityEvent marked = mock(EntityDamageByEntityEvent.class);
        EntityDamageByEntityEvent other = mock(EntityDamageByEntityEvent.class);
        EngineStores stores = EngineStores.fresh();
        ReforgeStrikeRelay.mark(marked, stores, UUID.randomUUID(), UUID.randomUUID(), ReforgeStrikeRelay.battery(null));

        assertNull(ReforgeStrikeRelay.consume(other, stores), "a different event never consumes the mark");
        assertTrue(ReforgeStrikeRelay.consume(marked, stores).batterySpend(), "the matching event still can");
    }

    @Test
    void builderAccumulatesThreeConcerns() {
        HitTempoStore.Window window = new HitTempoStore.Window(100L, 0, 0.5);
        ReforgeStrikeRelay.Pending pending =
                ReforgeStrikeRelay.battery(ReforgeStrikeRelay.disarm(ReforgeStrikeRelay.tempo(null, window)));

        assertTrue(pending.tempoHit());
        assertSame(window, pending.tempoWindow());
        assertTrue(pending.disarmStrike());
        assertTrue(pending.batterySpend());
    }

    @Test
    void emptyBuilderStartsClean() {
        ReforgeStrikeRelay.Pending pending = ReforgeStrikeRelay.tempo(null, new HitTempoStore.Window(50L, 1, 1.0));

        assertTrue(pending.tempoHit());
        assertFalse(pending.disarmStrike(), "tempo alone leaves the other concerns off");
        assertFalse(pending.batterySpend());
    }
}
