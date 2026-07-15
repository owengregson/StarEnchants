package feature.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import engine.stores.ComboStore;
import engine.stores.RageStackStore;
import feature.compat.Sounds;
import java.util.UUID;
import java.util.function.Function;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import platform.lang.Messages;
import platform.sched.Scheduling;
import testfx.RecordingSchedulerBackend;

/**
 * End-to-end lifecycle of a REAL {@link RageStacksService} — the seam the 1.8.7-beta fix exists to guarantee:
 * TAKING a hit ({@code onHitTaken}) breaks the run to zero (ADR-0058). Building climbs via {@code onHit} over the
 * shared {@link ComboStore} streak; a defense hit resets it, and a later rage hit rebuilds from 1. The clock is
 * injected, the expiry probe is captured by {@link RecordingSchedulerBackend}, and the fx statics degrade to
 * no-ops under mocks (a null action-bar fragment is null-safe through Colors.translate).
 */
class RageStacksServiceTest {

    private final long[] clock = {0};
    private int level = 5;
    private ComboStore combo;
    private RageStackStore store;
    private RageStacksService service;

    private final UUID aid = UUID.randomUUID();
    private final UUID vid = UUID.randomUUID();
    private final Player attacker = mock(Player.class);

    @BeforeEach
    void setUp() {
        Scheduling.install(new RecordingSchedulerBackend()); // capture the onHit expiry-probe hop
        combo = new ComboStore();
        store = new RageStackStore();
        Function<Player, Integer> rageLevelOf = p -> level;
        service = new RageStacksService(rageLevelOf, combo, store, mock(Messages.class), mock(Sounds.class),
                () -> clock[0]);
        when(attacker.getUniqueId()).thenReturn(aid);
    }

    /** Land one rage hit at {@code tick} (the dispatch advances the combo at HIGH before the MONITOR onHit). */
    private void rageHit(long tick) {
        clock[0] = tick;
        combo.hit(aid, vid, tick);
        service.onHit(attacker);
    }

    @Test
    void takingAHitBreaksTheBuiltRunToZero() {
        rageHit(0);
        rageHit(1);
        rageHit(2);
        assertEquals(3, store.current(aid)); // three consecutive rage hits

        service.onHitTaken(attacker);         // getting hit drops the whole combo
        assertEquals(0, store.current(aid));
    }

    @Test
    void aLoneStackAlsoDropsToZeroOnAHitTaken() {
        rageHit(0);
        assertEquals(1, store.current(aid));

        service.onHitTaken(attacker);         // a single stack resets silently, still to zero
        assertEquals(0, store.current(aid));
    }

    @Test
    void aHitTakenWithNoLiveRunIsANoOp() {
        service.onHitTaken(attacker);         // never raged → nothing to break, no throw
        assertEquals(0, store.current(aid));
    }

    @Test
    void theRunRebuildsFromOneAfterAReset() {
        rageHit(0);
        rageHit(1);
        service.onHitTaken(attacker);         // → 0
        assertEquals(0, store.current(aid));

        rageHit(2);                           // the next rage hit starts a fresh run
        assertEquals(1, store.current(aid));
    }
}
