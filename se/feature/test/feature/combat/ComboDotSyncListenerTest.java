package feature.combat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import engine.sink.DotParkLedger;
import java.util.List;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The MONITOR confirm + death clear (ADR-0069): death drops the victim's buckets AND the release in-flight
 * flag (the dead stay dead, ADR-0051), and the cancel re-park is identity-gated — a mark left for one event
 * is never consumed by another.
 */
class ComboDotSyncListenerTest {

    @AfterEach
    void clearRelay() {
        ParkFlushRelay.clear();
    }

    @Test
    void deathClearsTheVictimsLedger() {
        DotParkLedger ledger = new DotParkLedger();
        ComboDotSyncListener listener = new ComboDotSyncListener(ledger);
        UUID victimId = UUID.randomUUID();
        ledger.comboStarted(victimId, UUID.randomUUID(), 0L);
        ledger.tryPark(victimId, null, 3.0, 0L);
        assertTrue(ledger.beginRelease(victimId)); // a release was in flight when the victim died

        Player victim = mock(Player.class);
        when(victim.getUniqueId()).thenReturn(victimId);
        PlayerDeathEvent event = mock(PlayerDeathEvent.class);
        when(event.getEntity()).thenReturn(victim);
        listener.onDeath(event);

        assertFalse(ledger.hasParked(victimId), "banked damage dies with the victim");
        assertTrue(ledger.beginRelease(victimId), "the release in-flight flag was dropped by the death clear");
    }

    @Test
    void nonMarkedEventsAndForeignEventsAreIgnored() {
        DotParkLedger ledger = new DotParkLedger();
        ComboDotSyncListener listener = new ComboDotSyncListener(ledger);
        UUID victimId = UUID.randomUUID();
        List<DotParkLedger.Bucket> drained = List.of(new DotParkLedger.Bucket(UUID.randomUUID(), null, 2.0, 0L));

        EntityDamageByEntityEvent marked = mock(EntityDamageByEntityEvent.class);
        EntityDamageByEntityEvent foreign = mock(EntityDamageByEntityEvent.class);
        when(foreign.isCancelled()).thenReturn(true);
        ParkFlushRelay.mark(marked, ledger, victimId, drained);

        // A DIFFERENT (cancelled) event must not consume the mark left for `marked` — no restore fires.
        listener.onDamageResolved(foreign);
        assertFalse(ledger.hasParked(victimId), "a foreign event's cancel never re-parks another event's buckets");

        // The mark for `marked` survived — its own cancelled resolution restores it.
        when(marked.isCancelled()).thenReturn(true);
        listener.onDamageResolved(marked);
        assertTrue(ledger.hasParked(victimId), "the matching event's cancel re-parks the drained buckets");
    }
}
