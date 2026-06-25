package engine.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import engine.stores.CooldownStore;
import engine.stores.KeepOnDeathStore;
import engine.stores.KnockbackControlStore;
import engine.stores.SuppressionStore;
import engine.stores.VarStore;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import platform.economy.EconomyService;
import platform.resolve.RegistryResolvers;
import platform.resolve.RuntimeHandles;
import platform.sched.Scheduling;

/**
 * The {@link DispatchSink} policy (§3.6): damage-fold and cancel feedback are synchronous, but every world
 * mutation is deferred to flush and routed to its owning thread (the target may be a different entity/region),
 * preserving per-entity emission order. A {@link SyncSchedulerBackend} runs the deferred batches inline.
 */
class DispatchSinkTest {

    private RuntimeHandles handles;

    @BeforeEach
    void setUp() {
        handles = new RuntimeHandles(new RegistryResolvers());
        Scheduling.install(new SyncSchedulerBackend());
    }

    @Test
    void contributesToTheDamageFoldSynchronously() {
        DispatchSink sink = new DispatchSink(handles);
        sink.addOutgoingDamage(1.0);   // +100%
        sink.addFlatDamage(5.0);       // +5 flat, after the multiplier
        sink.addDamageReduction(0.5);  // -50% incoming
        sink.addFlatReduction(2.0);    // -2 flat, last

        // (10 x (1 + 1.0) + 5) x (1 - 0.5) - 2 = 25 x 0.5 - 2 = 10.5
        assertEquals(10.5, sink.fold().apply(10.0), 1e-9);
    }

    @Test
    void cancelEventSetsTheReadBackFlag() {
        DispatchSink sink = new DispatchSink(handles);
        assertFalse(sink.cancelled(), "a fresh sink must not be cancelled");
        sink.cancelEvent();
        assertTrue(sink.cancelled(), "cancelEvent must set the read-back flag");
    }

    @Test
    void worldMutationsAreDeferredUntilFlush() {
        LivingEntity target = mock(LivingEntity.class);

        DispatchSink sink = new DispatchSink(handles);
        sink.ignite(target, 60);
        verifyNoInteractions(target); // captured, never applied inline on the firing thread

        sink.flush();
        verify(target).setFireTicks(60);
    }

    @Test
    void batchesIntentsForTheSameEntityInEmissionOrder() {
        LivingEntity target = mock(LivingEntity.class);

        DispatchSink sink = new DispatchSink(handles);
        sink.ignite(target, 60);
        sink.extinguish(target);
        verifyNoInteractions(target);

        sink.flush();
        InOrder order = inOrder(target);
        order.verify(target).setFireTicks(60);
        order.verify(target).setFireTicks(0);
    }

    @Test
    void deferredDamageHopsToTheEntityOnFlush() {
        LivingEntity target = mock(LivingEntity.class);

        DispatchSink sink = new DispatchSink(handles);
        sink.damage(target, 7.5);
        verifyNoInteractions(target);

        sink.flush();
        verify(target).damage(7.5);
    }

    @Test
    void deferredTeleportRoutesThroughTheEntityScheduler() {
        Entity target = mock(Entity.class);
        Location to = mock(Location.class);
        when(to.clone()).thenReturn(to); // the sink clones the destination (it can outlive the tick under WAIT)

        DispatchSink sink = new DispatchSink(handles);
        sink.teleport(target, to);
        verifyNoInteractions(target);

        sink.flush();
        verify(target).teleportAsync(to);
    }

    @Test
    void flushIsIdempotent() {
        LivingEntity target = mock(LivingEntity.class);

        DispatchSink sink = new DispatchSink(handles);
        sink.ignite(target, 40);

        sink.flush();
        sink.flush();
        verify(target).setFireTicks(40);
    }

    @Test
    void ignoreArmorSetsTheReadBackFlag() {
        DispatchSink sink = new DispatchSink(handles);
        assertFalse(sink.armorIgnored(), "a fresh sink must not ignore armor");
        sink.ignoreArmor();
        assertTrue(sink.armorIgnored(), "ignoreArmor must set the read-back flag");
    }

    @Test
    void removeSoulsDefersToTheHolderThreadThenDebits() {
        Player holder = mock(Player.class);
        UUID gemId = UUID.randomUUID();
        int[] debited = {0};
        SoulDebit recording = (h, g, amount) -> {
            if (h == holder && g.equals(gemId)) {
                debited[0] += amount;
            }
        };

        DispatchSink sink = new DispatchSink(handles, EconomyService.NONE, recording, new VarStore(), new SuppressionStore(), () -> 0L);
        sink.removeSouls(holder, gemId, 5);
        assertEquals(0, debited[0], "the debit is captured, never applied inline on the firing thread");

        sink.flush();
        assertEquals(5, debited[0], "the debit runs on the holder's thread after flush");
    }

    @Test
    void removeSoulsIgnoresNullOrNonPositive() {
        Player holder = mock(Player.class);
        int[] calls = {0};
        SoulDebit recording = (h, g, amount) -> calls[0]++;

        DispatchSink sink = new DispatchSink(handles, EconomyService.NONE, recording, new VarStore(), new SuppressionStore(), () -> 0L);
        sink.removeSouls(null, UUID.randomUUID(), 5);
        sink.removeSouls(holder, null, 5);
        sink.removeSouls(holder, UUID.randomUUID(), 0);
        sink.flush();

        assertEquals(0, calls[0]);
    }

    @Test
    void setVarWritesThroughToTheStoreWithTheCapturedUuidAndTick() {
        Player holder = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(holder.getUniqueId()).thenReturn(id);
        VarStore store = new VarStore();

        DispatchSink sink = new DispatchSink(
                handles, EconomyService.NONE, SoulDebit.NONE, store, new SuppressionStore(), () -> 100L);
        sink.setVar(holder, "rage", "1", 0); // per-player state, written immediately (no flush needed)

        assertEquals("1", store.get(id, "rage", 100L));
    }

    @Test
    void invertVarWritesThroughToTheStore() {
        Player holder = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(holder.getUniqueId()).thenReturn(id);
        VarStore store = new VarStore();

        DispatchSink sink = new DispatchSink(
                handles, EconomyService.NONE, SoulDebit.NONE, store, new SuppressionStore(), () -> 0L);
        sink.invertVar(holder, "flag");

        assertEquals("1", store.get(id, "flag", 0L)); // unset → inverted to "1"
    }

    @Test
    void suppressWritesThroughToTheStoreWithThePackedScopeKey() {
        Player holder = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(holder.getUniqueId()).thenReturn(id);
        SuppressionStore store = new SuppressionStore();

        DispatchSink sink = new DispatchSink(
                handles, EconomyService.NONE, SoulDebit.NONE, new VarStore(), store, () -> 100L);
        sink.suppress(holder, 1, 7, 40); // GROUP(1) scope id 7, for 40 ticks

        assertTrue(store.isSuppressed(id, CooldownStore.key(1, 7), 100L));
        assertFalse(store.isSuppressed(id, CooldownStore.key(1, 7), 140L)); // expires at 140
    }

    @Test
    void controlKnockbackWritesThroughToTheStoreWithTheCapturedUuidAndTick() {
        LivingEntity victim = mock(LivingEntity.class);
        UUID id = UUID.randomUUID();
        when(victim.getUniqueId()).thenReturn(id);
        KnockbackControlStore store = new KnockbackControlStore();

        DispatchSink sink = new DispatchSink(handles, EconomyService.NONE, SoulDebit.NONE,
                new VarStore(), new SuppressionStore(), store, () -> 100L);
        sink.controlKnockback(victim, 0.0, 5); // per-victim state, written immediately (no flush needed)

        assertEquals(0.0, store.multiplier(id, 100L)); // 0.0 is an active full-cancel, distinct from NaN "no control"
        assertTrue(Double.isNaN(store.multiplier(id, 105L)), "expires at tick 105");
    }

    @Test
    void keepOnDeathWritesThroughToTheStoreWithTheCapturedUuidAndTick() {
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        KeepOnDeathStore store = new KeepOnDeathStore();

        DispatchSink sink = new DispatchSink(handles, EconomyService.NONE, SoulDebit.NONE, new VarStore(),
                new SuppressionStore(), new KnockbackControlStore(), store, () -> 100L);
        sink.keepOnDeath(player, 40); // per-player flag, written immediately (no flush needed)

        assertTrue(store.shouldKeep(id, 100L));
        assertFalse(store.shouldKeep(id, 140L), "expires at tick 140");
    }
}
