package engine.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import compile.model.ScopeKinds;
import engine.stores.CooldownStore;
import engine.stores.EngineStores;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import platform.sched.Scheduling;
import testfx.Envs;
import testfx.SyncSchedulerBackend;

/**
 * R-QC15: gate 6 reserves before the effects run, so a refusal only the PAYLOAD can establish — a conversion
 * that converted nothing — arrives too late for any condition. The refund is the release of exactly that
 * reservation, and nothing else: a stale call, or one naming a window this ability did not arm, must miss.
 */
class RefundCooldownTest {

    private static final int SCOPE = 12;
    private static final int DURATION = 6000; // the elemental pets' five minutes

    private EngineStores stores;
    private RecordingSink sink;
    private Player actor;
    private UUID actorId;
    private long now;

    @BeforeEach
    void setUp() {
        Scheduling.install(new SyncSchedulerBackend());
        stores = EngineStores.fresh();
        now = 100L;
        sink = new RecordingSink(Envs.sink().stores(stores).nowTicks(() -> now).build());
        actorId = UUID.randomUUID();
        actor = mock(Player.class);
        when(actor.getUniqueId()).thenReturn(actorId);
    }

    private long key() {
        return CooldownStore.key(ScopeKinds.ENCHANT, SCOPE, 0);
    }

    private long reserve() {
        return stores.cooldowns().tryAcquire(actorId, null, key(), now, DURATION);
    }

    @Test
    void theRefundReleasesTheReservationTheGateJustMade() {
        assertEquals(0L, reserve(), "the gate acquires the window");
        assertEquals(DURATION, stores.cooldowns().remainingTicks(actorId, key(), now));

        sink.refundCooldown(actor, SCOPE, 0, null, DURATION);
        sink.flush();

        assertEquals(0L, stores.cooldowns().remainingTicks(actorId, key(), now),
                "a click whose payload did nothing costs nothing");
        assertEquals(0L, reserve(), "and the very next click can acquire it again");
    }

    @Test
    void aRefundAtTheWrongTickMissesRatherThanClearingAWindowItDidNotArm() {
        // The release is value-matched on the expiry the gate wrote. Failing this way round is the safe one:
        // a missed refund charges a cooldown, where a loose one would clear a window somebody else armed.
        reserve();
        now = 140L; // e.g. a WAIT tier deferred the refund off the arming tick

        sink.refundCooldown(actor, SCOPE, 0, null, DURATION);
        sink.flush();

        assertEquals(DURATION - 40, stores.cooldowns().remainingTicks(actorId, key(), now));
    }

    @Test
    void anAbilityWithNoCooldownAtAllRefundsNothing() {
        // The default ctx values (-1 scope, 0 ticks) reach the sink on every ability that carries no cooldown;
        // they must be inert rather than releasing key(-1) or key(scope, 0).
        reserve();

        sink.refundCooldown(actor, -1, 0, null, DURATION);
        sink.refundCooldown(actor, SCOPE, 0, null, 0);
        sink.flush();

        assertEquals(DURATION, stores.cooldowns().remainingTicks(actorId, key(), now));
    }

    @Test
    void theRefundReleasesThePlayerRouteAndThePerVictimDimensionItWasArmedIn() {
        // The key gate 6 wrote carries the target bucket (1 whenever the other combat party is a player) and,
        // for a cooldown-per-victim ability, lives in that victim's own dimension. A refund that assumed the
        // coarse bucket-0 key released nothing on exactly the hits the effect exists to forgive.
        UUID victim = UUID.randomUUID();
        long pvpKey = CooldownStore.key(ScopeKinds.ENCHANT, SCOPE, 1);
        assertEquals(0L, stores.cooldowns().tryAcquire(actorId, victim, pvpKey, now, DURATION));

        sink.refundCooldown(actor, SCOPE, 1, victim, DURATION);
        sink.flush();

        assertEquals(0L, stores.cooldowns().remainingTicks(actorId, victim, pvpKey, now));
    }
}
