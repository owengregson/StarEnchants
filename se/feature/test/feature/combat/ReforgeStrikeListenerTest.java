package feature.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.intThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import compile.load.ContentHolder;
import compile.model.Snapshot;
import engine.sink.EngineDamage;
import engine.stores.EngineStores;
import engine.stores.HitTempoStore;
import feature.compat.Sounds;
import item.worn.WornStateStore;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import platform.lang.Messages;
import platform.sched.Scheduling;
import testfx.Snapshots;
import testfx.SyncSchedulerBackend;

/**
 * The MONITOR commit + bank side of the reforge armed windows (ADR-0071 §2.6): a landed hit spends the
 * Supernova core / shuffles the Unhanding victim / rewrites the Quickening i-frames; a cancel keeps them all
 * armed; a Supernova-armed victim banks a fraction of what they take, but only where a discharge could also
 * occur (the symmetric context gate). All state assertions — never display strings (writing-tests Rule 1).
 */
class ReforgeStrikeListenerTest {

    private final EngineStores stores = EngineStores.fresh();
    private final WornStateStore worn = mock(WornStateStore.class);
    private final ContentHolder content = mock(ContentHolder.class);
    private final Messages messages = mock(Messages.class);
    private final Sounds sounds = mock(Sounds.class);
    private final long[] clock = {0};
    private final boolean[] pvp = {true};
    private final boolean[] pve = {true};

    private ReforgeStrikeListener listener;

    @BeforeEach
    void setUp() {
        // The tempo i-frame write hops one tick (it must survive vanilla's post-event invulnerableTime reset);
        // the sync backend runs that hop inline so the write is observable in-test.
        Scheduling.install(new SyncSchedulerBackend());
        listener = new ReforgeStrikeListener(stores, worn, content, messages, sounds, () -> clock[0],
                () -> pvp[0], () -> pve[0]);
    }

    @AfterEach
    void tearDown() {
        ReforgeStrikeRelay.clear();
        ReHitGuard.clearSkipped();
        CombatDispatch.friendlyFire(null);
    }

    private static Player player(UUID id) {
        Player p = mock(Player.class);
        when(p.getUniqueId()).thenReturn(id);
        return p;
    }

    private EntityDamageByEntityEvent damageEvent(Entity damager, LivingEntity victim, boolean cancelled,
                                                  double finalDamage) {
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getDamager()).thenReturn(damager);
        when(event.getEntity()).thenReturn(victim);
        when(event.isCancelled()).thenReturn(cancelled);
        when(event.getFinalDamage()).thenReturn(finalDamage);
        return event;
    }

    @Test
    void landedDisarmShufflesConsumesAndRefreshes() {
        UUID vid = UUID.randomUUID();
        UUID aid = UUID.randomUUID();
        stores.disarmWindows().arm(aid, 0L, 80, 0.20);
        Player victim = player(vid);
        PlayerInventory inv = mock(PlayerInventory.class);
        when(victim.getInventory()).thenReturn(inv);
        when(victim.getLocation()).thenReturn(mock(Location.class));
        when(inv.getHeldItemSlot()).thenReturn(0);
        ItemStack heldItem = mock(ItemStack.class);
        ItemStack otherItem = mock(ItemStack.class);
        when(inv.getItem(0)).thenReturn(heldItem);
        when(inv.getItem(intThat(s -> s != 0))).thenReturn(otherItem);
        Player attacker = player(aid);
        Snapshot snapshot = Snapshots.snapshot().build();
        when(content.snapshot()).thenReturn(snapshot);
        EntityDamageByEntityEvent event = damageEvent(attacker, victim, false, 0.0);
        ReforgeStrikeRelay.mark(event, stores, aid, vid, ReforgeStrikeRelay.disarm(null));

        listener.onDamageResolved(event);

        assertNull(stores.disarmWindows().armed(aid, 0L), "the one-shot window is consumed");
        verify(inv).setItem(eq(0), eq(otherItem));                 // the held slot now holds what was elsewhere
        verify(inv).setItem(intThat(s -> s != 0), eq(heldItem));   // the weapon shuffled to another slot
        verify(victim).updateInventory();
        verify(worn).refresh(victim, snapshot);                    // held-weapon abilities/rage must not read stale
        verify(messages).fragment("reforge.unhanding.shuffled-victim");
        verify(messages).fragment("reforge.unhanding.struck");
    }

    @Test
    void cancelledHitKeepsWindowAndCore() {
        UUID vid = UUID.randomUUID();
        UUID aid = UUID.randomUUID();
        stores.disarmWindows().arm(aid, 0L, 80, 0.20);
        stores.battery().arm(aid, 1.0, 3);
        stores.battery().bank(aid, 4.5);
        Player victim = player(vid);
        Player attacker = player(aid);
        EntityDamageByEntityEvent event = damageEvent(attacker, victim, true, 0.0);
        ReforgeStrikeRelay.mark(event, stores, aid, vid,
                ReforgeStrikeRelay.battery(ReforgeStrikeRelay.disarm(null)));

        listener.onDamageResolved(event);

        assertNotNull(stores.disarmWindows().armed(aid, 0L), "a cancelled hit keeps the Unhanding armed");
        assertEquals(4.5, stores.battery().peek(aid), 1e-9, "a cancelled hit keeps the core charged");
        verifyNoInteractions(worn);
    }

    @Test
    void landedBatterySpendDischargesOnce() {
        UUID vid = UUID.randomUUID();
        UUID aid = UUID.randomUUID();
        stores.battery().arm(aid, 1.0, 3);
        stores.battery().bank(aid, 4.5);
        Player victim = player(vid);
        when(victim.getLocation()).thenReturn(mock(Location.class));
        Player attacker = player(aid);
        EntityDamageByEntityEvent event = damageEvent(attacker, victim, false, 0.0);
        ReforgeStrikeRelay.mark(event, stores, aid, vid, ReforgeStrikeRelay.battery(null));

        listener.onDamageResolved(event);
        assertFalse(stores.battery().armed(aid), "the core spent");
        verify(messages).fragment(eq("reforge.battery.discharged"), eq("AMOUNT"), anyString());

        listener.onDamageResolved(event); // the relay mark was consumed — the second delivery is a no-op
        verify(messages, times(1)).fragment(eq("reforge.battery.discharged"), eq("AMOUNT"), anyString());
    }

    @Test
    void banksTwentyPercentOfFinalDamage() {
        UUID vid = UUID.randomUUID();
        UUID aid = UUID.randomUUID();
        stores.battery().arm(vid, 0.2, 3);
        Player victim = player(vid);
        Player attacker = player(aid);
        EntityDamageByEntityEvent event = damageEvent(attacker, victim, false, 12.5);

        listener.onDamageResolved(event);

        assertEquals(2.5, stores.battery().peek(vid), 1e-9, "0.2 × the final damage banked");
        verify(messages).fragment(eq("reforge.battery.banked-actionbar"), eq("STORED"), anyString(),
                eq("HITS"), eq(2));
    }

    @Test
    void bankSkipsEngineDamageAndReHitSkips() {
        UUID vid = UUID.randomUUID();
        stores.battery().arm(vid, 0.2, 3);
        Player victim = player(vid);
        Player attacker = player(UUID.randomUUID());
        EntityDamageByEntityEvent event = damageEvent(attacker, victim, false, 12.5);

        EngineDamage.frame(() -> listener.onDamageResolved(event)); // an engine DoT/reflect tick
        assertEquals(0.0, stores.battery().peek(vid), 1e-9, "no bank inside an engine-damage frame");

        ReHitGuard.markSkipped(event); // a same-swing "damage the difference" re-hit
        try {
            listener.onDamageResolved(event);
        } finally {
            ReHitGuard.clearSkipped();
        }
        assertEquals(0.0, stores.battery().peek(vid), 1e-9, "no bank on a same-swing duplicate");
    }

    @Test
    void bankSkipsDisabledContextAndFriendlyHits() {
        UUID vid = UUID.randomUUID();
        stores.battery().arm(vid, 0.2, 3);
        Player victim = player(vid);
        Player attacker = player(UUID.randomUUID());

        pvp[0] = false; // pvp off, a player damager → no bank
        listener.onDamageResolved(damageEvent(attacker, victim, false, 12.5));
        assertEquals(0.0, stores.battery().peek(vid), 1e-9, "pvp off — a charge that could never discharge never banks");

        pvp[0] = true;
        CombatDispatch.friendlyFire((a, v) -> true); // friendly pair → no bank
        listener.onDamageResolved(damageEvent(attacker, victim, false, 12.5));
        assertEquals(0.0, stores.battery().peek(vid), 1e-9, "friendly fire — no bank");

        CombatDispatch.friendlyFire(null);
        pve[0] = false; // pve off, a mob damager → no bank
        LivingEntity mob = mock(LivingEntity.class);
        when(mob.getUniqueId()).thenReturn(UUID.randomUUID());
        listener.onDamageResolved(damageEvent(mob, victim, false, 12.5));
        assertEquals(0.0, stores.battery().peek(vid), 1e-9, "pve off — a mob hit never banks");
    }

    @Test
    void tempoWriteMentalModel() {
        UUID vid = UUID.randomUUID();
        UUID aid = UUID.randomUUID();
        Player victim = player(vid);
        when(victim.getMaximumNoDamageTicks()).thenReturn(20);
        Player attacker = player(aid);
        EntityDamageByEntityEvent event = damageEvent(attacker, victim, false, 0.0);
        ReforgeStrikeRelay.mark(event, stores, aid, vid,
                ReforgeStrikeRelay.tempo(null, new HitTempoStore.Window(100L, 0, 1.0 / 3.0)));

        listener.onDamageResolved(event);

        verify(victim).setNoDamageTicks(15); // max 20, MENTAL W=10 → 20 − 10/2
        assertTrue(stores.hitTempo().stolenBlocks(vid, UUID.randomUUID(), 9L), "stamped until now+max/2 = 10");
        assertFalse(stores.hitTempo().stolenBlocks(vid, UUID.randomUUID(), 10L), "lapsed at 10");
    }

    @Test
    void tempoWriteVanillaModel() {
        UUID vid = UUID.randomUUID();
        UUID aid = UUID.randomUUID();
        Player victim = player(vid);
        when(victim.getMaximumNoDamageTicks()).thenReturn(20);
        Player attacker = player(aid);
        EntityDamageByEntityEvent event = damageEvent(attacker, victim, false, 0.0);
        ReforgeStrikeRelay.mark(event, stores, aid, vid,
                ReforgeStrikeRelay.tempo(null, new HitTempoStore.Window(100L, 1, 1.0 / 3.0)));

        listener.onDamageResolved(event);

        verify(victim).setNoDamageTicks(10); // max 20, VANILLA W=20 → 20 − 20/2
    }

    @Test
    void tempoWriteOddMax() {
        UUID vid = UUID.randomUUID();
        UUID aid = UUID.randomUUID();
        Player victim = player(vid);
        when(victim.getMaximumNoDamageTicks()).thenReturn(19);
        Player attacker = player(aid);
        EntityDamageByEntityEvent event = damageEvent(attacker, victim, false, 0.0);
        ReforgeStrikeRelay.mark(event, stores, aid, vid,
                ReforgeStrikeRelay.tempo(null, new HitTempoStore.Window(100L, 0, 1.0 / 3.0)));

        listener.onDamageResolved(event);

        verify(victim).setNoDamageTicks(15); // max 19, MENTAL W=9 → 19 − 9/2 = 19 − 4
        assertTrue(stores.hitTempo().stolenBlocks(vid, UUID.randomUUID(), 8L), "stamped until now+max/2 = 9");
        assertFalse(stores.hitTempo().stolenBlocks(vid, UUID.randomUUID(), 9L), "lapsed at 9");
    }

    @Test
    void deathClearsBattery() {
        UUID vid = UUID.randomUUID();
        stores.battery().arm(vid, 0.2, 3);
        Player victim = player(vid);
        PlayerDeathEvent event = mock(PlayerDeathEvent.class);
        when(event.getEntity()).thenReturn(victim);

        listener.onDeath(event);

        assertFalse(stores.battery().armed(vid), "banked charge dies with the victim (ADR-0051)");
    }

    @Test
    void shuffleTargetCoversAllOtherSlots() {
        for (int held = 0; held <= 8; held++) {
            Set<Integer> targets = new HashSet<>();
            for (int roll = 0; roll <= 7; roll++) {
                int target = ReforgeStrikeListener.shuffleTarget(held, roll);
                assertTrue(target >= 0 && target <= 8, "in the hotbar for held=" + held);
                assertNotEquals(held, target, "never the held slot for held=" + held);
                targets.add(target);
            }
            assertEquals(8, targets.size(), "the 8 rolls hit 8 distinct slots for held=" + held);
        }
    }
}
