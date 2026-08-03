package engine.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import engine.stores.CooldownStore;
import engine.stores.DamageCapStore;
import engine.stores.KeepOnDeathStore;
import engine.stores.KnockbackControlStore;
import engine.stores.OutgoingDebuffStore;
import engine.stores.ReflectMarksStore;
import engine.stores.SuppressionStore;
import engine.stores.VarStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.mockito.InOrder;
import platform.economy.EconomyProvider;
import platform.economy.EconomyService;
import platform.resolve.RegistryResolvers;
import platform.resolve.RuntimeHandles;
import platform.sched.Scheduling;
import testfx.Envs;
import testfx.SyncSchedulerBackend;

/**
 * The {@link ModernDispatchSink} policy (§3.6): damage-fold and cancel feedback are synchronous, but every world
 * mutation is deferred to flush and routed to its owning thread (the target may be a different entity/region),
 * preserving per-entity emission order. A {@link testfx.SyncSchedulerBackend} runs the deferred batches inline.
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
        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().build());
        sink.addOutgoingDamage(1.0);   // +100%
        sink.addFlatDamage(5.0);       // +5 flat, after the multiplier
        sink.addDamageReduction(0.5);  // -50% incoming
        sink.addFlatReduction(2.0);    // -2 flat, last

        // (10 x (1 + 1.0) + 5) x (1 - 0.5) - 2 = 25 x 0.5 - 2 = 10.5
        assertEquals(10.5, sink.fold().apply(10.0), 1e-9);
    }

    @Test
    void cancelEventSetsTheReadBackFlag() {
        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().build());
        assertFalse(sink.cancelled(), "a fresh sink must not be cancelled");
        sink.cancelEvent();
        assertTrue(sink.cancelled(), "cancelEvent must set the read-back flag");
    }

    @Test
    void worldMutationsAreDeferredUntilFlush() {
        LivingEntity target = mock(LivingEntity.class);

        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().build());
        sink.ignite(target, 60);
        verifyNoInteractions(target); // captured, never applied inline on the firing thread

        sink.flush();
        verify(target).setFireTicks(60);
    }

    @Test
    void batchesIntentsForTheSameEntityInEmissionOrder() {
        LivingEntity target = mock(LivingEntity.class);

        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().build());
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

        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().build());
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

        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().build());
        sink.teleport(target, to);
        verifyNoInteractions(target);

        sink.flush();
        verify(target).teleportAsync(to);
    }

    @Test
    void flushIsIdempotent() {
        LivingEntity target = mock(LivingEntity.class);

        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().build());
        sink.ignite(target, 40);

        sink.flush();
        sink.flush();
        verify(target).setFireTicks(40);
    }

    @Test
    void ignoreArmorSetsTheReadBackFlag() {
        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().build());
        assertFalse(sink.armorIgnored(), "a fresh sink must not ignore armor");
        sink.ignoreArmor();
        assertTrue(sink.armorIgnored(), "ignoreArmor must set the read-back flag");
    }

    @Test
    void requestEchoStrikeSetsTheReadBackFlag() {
        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().build());
        assertFalse(sink.echoRequested(), "a fresh sink must not request an echo pass");
        sink.requestEchoStrike();
        assertTrue(sink.echoRequested(), "requestEchoStrike must set the read-back flag the dispatcher reads (ECHO_STRIKE)");
    }

    @Test
    void reflectMarkWritesTheReflectStoreInline() {
        ReflectMarksStore store = new ReflectMarksStore();
        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().reflectMarks(store).nowTicks(() -> 0L).build());
        Player p = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(p.getUniqueId()).thenReturn(id);
        sink.reflectMark(p, 20.0, 0, "", 80); // inline (no flush) — a per-player window write, not a deferred intent
        assertEquals(20.0, store.active(id, 0L).fractionPercent());
    }

    @Test
    void weakenWritesTheOutgoingDebuffStoreInline() {
        OutgoingDebuffStore store = new OutgoingDebuffStore();
        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().outgoingDebuff(store).nowTicks(() -> 0L).build());
        Player p = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(p.getUniqueId()).thenReturn(id);
        sink.weaken(p, 15.0, 100);
        assertEquals(15.0, store.active(id, 0L));
    }

    @Test
    void armDamageCapComputesCapFromLastTakenTimesFactor() {
        DamageCapStore store = new DamageCapStore();
        Player p = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(p.getUniqueId()).thenReturn(id);
        store.recordLastTaken(id, 10.0);
        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().damageCap(store).nowTicks(() -> 0L).build());
        sink.armDamageCap(p, 0.5, true, 100);
        DamageCapStore.Cap cap = store.consumeArmed(id, 0L);
        assertEquals(5.0, cap.value(), "the cap is fixed at last-taken × factor at arm time");
        assertTrue(cap.reflectOverflow());
    }

    @Test
    void armDamageCapWithNoLastTakenHistoryArmsNothing() {
        DamageCapStore store = new DamageCapStore();
        Player p = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(p.getUniqueId()).thenReturn(id);
        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().damageCap(store).nowTicks(() -> 0L).build());
        sink.armDamageCap(p, 0.5, false, 100); // no recorded hit → value 0 → nothing armed
        assertNull(store.consumeArmed(id, 0L));
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

        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().souls(recording).build());
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

        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().souls(recording).build());
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

        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().vars(store).nowTicks(() -> 100L).build());
        sink.setVar(holder, "rage", "1", 0); // per-player state, written immediately (no flush needed)

        assertEquals("1", store.get(id, "rage", 100L));
    }

    @Test
    void invertVarWritesThroughToTheStore() {
        Player holder = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(holder.getUniqueId()).thenReturn(id);
        VarStore store = new VarStore();

        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().vars(store).build());
        sink.invertVar(holder, "flag");

        assertEquals("1", store.get(id, "flag", 0L)); // unset → inverted to "1"
    }

    @Test
    void suppressWritesThroughToTheStoreWithThePackedScopeKey() {
        Player holder = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(holder.getUniqueId()).thenReturn(id);
        SuppressionStore store = new SuppressionStore();

        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().suppression(store).nowTicks(() -> 100L).build());
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

        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().knockback(store).nowTicks(() -> 100L).build());
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

        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().keepOnDeath(store).nowTicks(() -> 100L).build());
        sink.keepOnDeath(player, 40); // per-player flag, written immediately (no flush needed)

        assertTrue(store.shouldKeep(id, 100L));
        assertFalse(store.shouldKeep(id, 140L), "expires at tick 140");
    }

    // ── F31: transferMoney moves at most the victim's balance, never minting on a broke victim ──

    /** A trivial in-memory economy: withdraw is all-or-nothing (matches the real provider contract). */
    private static EconomyService economyOf(Map<UUID, Double> balances) {
        return new EconomyService(new EconomyProvider() {
            @Override
            public double balance(UUID player) {
                return balances.getOrDefault(player, 0.0);
            }

            @Override
            public boolean withdraw(UUID player, double amount) {
                if (amount <= 0) {
                    return true;
                }
                double bal = balances.getOrDefault(player, 0.0);
                if (bal < amount) {
                    return false;
                }
                balances.put(player, bal - amount);
                return true;
            }

            @Override
            public void deposit(UUID player, double amount) {
                if (amount > 0) {
                    balances.merge(player, amount, Double::sum);
                }
            }
        });
    }

    @Test
    void transferMoneyClampsToTheVictimsBalanceSoNothingIsMinted() {
        UUID fromId = UUID.randomUUID();
        UUID toId = UUID.randomUUID();
        Player from = mock(Player.class);
        Player to = mock(Player.class);
        when(from.getUniqueId()).thenReturn(fromId);
        when(to.getUniqueId()).thenReturn(toId);
        Map<UUID, Double> balances = new HashMap<>(Map.of(fromId, 30.0, toId, 0.0));

        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().economy(economyOf(balances)).build());
        sink.transferMoney(from, to, 50.0); // asks for 50, victim only holds 30
        sink.flush();

        assertEquals(0.0, balances.get(fromId), 1e-9, "victim charged exactly what they held");
        assertEquals(30.0, balances.get(toId), 1e-9, "actor credited only what was charged — no minting");
    }

    @Test
    void transferMoneyFromABrokeVictimMovesNothing() {
        UUID fromId = UUID.randomUUID();
        UUID toId = UUID.randomUUID();
        Player from = mock(Player.class);
        Player to = mock(Player.class);
        when(from.getUniqueId()).thenReturn(fromId);
        when(to.getUniqueId()).thenReturn(toId);
        Map<UUID, Double> balances = new HashMap<>(Map.of(fromId, 0.0, toId, 5.0));

        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().economy(economyOf(balances)).build());
        sink.transferMoney(from, to, 50.0);
        sink.flush();

        assertEquals(0.0, balances.get(fromId), 1e-9);
        assertEquals(5.0, balances.get(toId), 1e-9, "a broke victim credits the actor nothing");
    }

    @Test
    void transferMoneyMovesTheFullAmountFromARichVictim() {
        UUID fromId = UUID.randomUUID();
        UUID toId = UUID.randomUUID();
        Player from = mock(Player.class);
        Player to = mock(Player.class);
        when(from.getUniqueId()).thenReturn(fromId);
        when(to.getUniqueId()).thenReturn(toId);
        Map<UUID, Double> balances = new HashMap<>(Map.of(fromId, 100.0, toId, 0.0));

        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().economy(economyOf(balances)).build());
        sink.transferMoney(from, to, 50.0);
        sink.flush();

        assertEquals(50.0, balances.get(fromId), 1e-9);
        assertEquals(50.0, balances.get(toId), 1e-9);
    }

    @Test
    void selfTransferMoneyIsNetZero() {
        UUID id = UUID.randomUUID();
        Player self = mock(Player.class);
        when(self.getUniqueId()).thenReturn(id);
        Map<UUID, Double> balances = new HashMap<>(Map.of(id, 30.0));

        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().economy(economyOf(balances)).build());
        sink.transferMoney(self, self, 50.0); // the who:@Self printer variant
        sink.flush();

        assertEquals(30.0, balances.get(id), 1e-9, "withdraw then deposit onto the same account nets zero");
    }

    // ── F32: transferExp moves at most the victim's real total, computed from the vanilla curve ──

    @Test
    void transferExpMovesAtMostTheVictimsRealTotal() {
        Player victim = mock(Player.class);
        Player actor = mock(Player.class);
        when(victim.getLevel()).thenReturn(1); // level 1 = 7 points on the vanilla curve
        when(victim.getExp()).thenReturn(0f);
        when(victim.getExpToLevel()).thenReturn(9);

        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().build());
        sink.transferExp(victim, actor, 50); // asks for 50, victim only holds 7
        sink.flush();

        verify(victim).giveExp(-7); // clamped to what the victim actually holds
        verify(actor).giveExp(7);   // actor credited exactly what was withdrawn — nothing minted
    }

    @Test
    void transferExpFromABrokeVictimMintsNothing() {
        Player victim = mock(Player.class);
        Player actor = mock(Player.class);
        when(victim.getLevel()).thenReturn(0);
        when(victim.getExp()).thenReturn(0f);
        when(victim.getExpToLevel()).thenReturn(7);

        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().build());
        sink.transferExp(victim, actor, 50);
        sink.flush();

        verify(victim, never()).giveExp(anyInt());
        verify(actor, never()).giveExp(anyInt());
    }

    @Test
    void transferExpMovesTheFullAmountFromARichVictim() {
        Player victim = mock(Player.class);
        Player actor = mock(Player.class);
        when(victim.getLevel()).thenReturn(40); // far more than 50 points
        when(victim.getExp()).thenReturn(0f);
        when(victim.getExpToLevel()).thenReturn(9);

        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().build());
        sink.transferExp(victim, actor, 50);
        sink.flush();

        verify(victim).giveExp(-50);
        verify(actor).giveExp(50);
    }

    @TestFactory
    List<DynamicTest> totalXpPointsFollowsTheVanillaCurve() {
        // level → total points at the start of that level (three curve segments: <=16, 17-31, >=32).
        int[][] rows = {{0, 0}, {1, 7}, {16, 352}, {17, 394}, {31, 1507}, {32, 1628}};
        List<DynamicTest> tests = new ArrayList<>();
        for (int[] row : rows) {
            tests.add(dynamicTest("level " + row[0] + " → " + row[1] + " points", () -> {
                Player p = mock(Player.class);
                when(p.getLevel()).thenReturn(row[0]);
                when(p.getExp()).thenReturn(0f);
                when(p.getExpToLevel()).thenReturn(1);
                assertEquals(row[1], DispatchSinkBase.totalXpPoints(p));
            }));
        }
        tests.add(dynamicTest("fractional progress adds round(exp × expToLevel) into the current level", () -> {
            Player p = mock(Player.class);
            when(p.getLevel()).thenReturn(5); // 5*5 + 6*5 = 55
            when(p.getExp()).thenReturn(0.5f);
            when(p.getExpToLevel()).thenReturn(17);
            assertEquals(55 + Math.round(0.5f * 17), DispatchSinkBase.totalXpPoints(p));
        }));
        return tests;
    }
}
