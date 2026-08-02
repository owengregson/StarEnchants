package feature.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import compile.load.ContentHolder;
import feature.trigger.LifecycleDriver;
import feature.trigger.MaxHealthDriver;
import feature.trigger.PassiveEffectDriver;
import feature.trigger.RepeatingDriver;
import feature.trigger.SetMessageDriver;
import item.codec.CombatCodec;
import item.codec.CombatState;
import item.view.ItemViewCache;
import item.worn.EquipSource;
import item.worn.WornStateStore;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import platform.sched.Scheduling;
import testfx.RecordingSchedulerBackend;

/**
 * Unit-pins the §B {@link EquipListener} hand-mutation feeders (G02/G03) and the F09 hand-signature reconcile.
 * The close / drop / item-break handlers schedule exactly one DEFERRED refresh (never synchronous — the break's
 * same-tick BREAK trigger must still see the item). {@link EquipListener#reconcile} refreshes on a combat-content
 * change but NOT on a durability-only change (the regression guard: a durability tick must not restart REPEATING
 * abilities). A {@link RecordingSchedulerBackend} captures the deferred hops; the per-event live behaviour is in
 * the matrix suite.
 */
class EquipListenerTest {

    private RecordingSchedulerBackend backend;
    private WornStateStore worn;
    private RepeatingDriver repeating;
    private LifecycleDriver lifecycle;
    private PassiveEffectDriver passiveEffects;
    private MaxHealthDriver maxHealth;
    private SetMessageDriver setMessages;
    private CombatCodec codec;
    private ScriptedEquipSource equip;
    private EquipListener listener;
    private Player player;
    private UUID uuid;

    /** A mutable equipment read: the 5-slot array (index 4 = the single hand) is swapped per test. */
    private static final class ScriptedEquipSource implements EquipSource {
        ItemStack[] gear = new ItemStack[5];

        @Override
        public ItemStack[] snapshot(LivingEntity entity) {
            return gear;
        }
    }

    @BeforeEach
    void setUp() {
        backend = new RecordingSchedulerBackend();
        Scheduling.install(backend);

        worn = mock(WornStateStore.class);
        repeating = mock(RepeatingDriver.class);
        lifecycle = mock(LifecycleDriver.class);
        passiveEffects = mock(PassiveEffectDriver.class);
        maxHealth = mock(MaxHealthDriver.class);
        setMessages = mock(SetMessageDriver.class);
        ContentHolder content = mock(ContentHolder.class);
        codec = mock(CombatCodec.class);
        ItemViewCache itemViews = new ItemViewCache(codec, 0);
        equip = new ScriptedEquipSource();

        listener = new EquipListener(worn, content, repeating, lifecycle, passiveEffects, maxHealth,
                setMessages, equip, itemViews);

        uuid = UUID.randomUUID();
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
    }

    /** Bind an ItemStack to a decoded combat blob so the ItemViewCache resolves its {@link CombatState}. */
    private ItemStack stack(String blob, CombatState combat) {
        ItemStack item = mock(ItemStack.class);
        when(codec.readBlob(item)).thenReturn(blob);
        when(codec.decode(blob)).thenReturn(combat);
        return item;
    }

    private static CombatState rage() {
        return new CombatState(Map.of("enchants/rage", 1), List.of());
    }

    private static CombatState guard() {
        return new CombatState(Map.of("enchants/guard", 1), List.of());
    }

    @Test
    void onlyTheHotbarSlotChangeFiresTheHeldChangeHook() {
        // %heldticks% means "since you swapped weapons". refresh() also fires for a chest close, a drop and an
        // armour swap, so the hook has to be the narrow one or closing a chest would read as a swap.
        List<Player> stamped = new java.util.ArrayList<>();
        listener.onHeldChange(stamped::add);

        InventoryCloseEvent close = mock(InventoryCloseEvent.class);
        when(close.getPlayer()).thenReturn(player);
        listener.onInventoryClose(close);
        assertTrue(stamped.isEmpty(), "closing a chest is not a weapon swap");

        org.bukkit.event.player.PlayerItemHeldEvent held =
                mock(org.bukkit.event.player.PlayerItemHeldEvent.class);
        when(held.getPlayer()).thenReturn(player);
        listener.onHeldChange(held);

        assertEquals(List.of(player), stamped, "stamped on the event itself, not on the deferred refresh");
    }

    @Test
    void inventoryCloseSchedulesExactlyOneDeferredRefresh() {
        InventoryCloseEvent event = mock(InventoryCloseEvent.class);
        when(event.getPlayer()).thenReturn(player);

        listener.onInventoryClose(event);

        assertEquals(1, backend.delayed.size(), "one deferred refresh, never synchronous");
        assertEquals(1L, backend.delayed.get(0).delayTicks());
    }

    @Test
    void dropSchedulesExactlyOneDeferredRefresh() {
        PlayerDropItemEvent event = mock(PlayerDropItemEvent.class);
        when(event.getPlayer()).thenReturn(player);

        listener.onDrop(event);

        assertEquals(1, backend.delayed.size());
        assertEquals(1L, backend.delayed.get(0).delayTicks());
    }

    @Test
    void itemBreakSchedulesADeferredRefreshNeverSynchronous() {
        // The defer is load-bearing (G03): the same-tick BREAK trigger must still see the item before teardown.
        PlayerItemBreakEvent event = mock(PlayerItemBreakEvent.class);
        when(event.getPlayer()).thenReturn(player);

        listener.onItemBreak(event);

        assertEquals(1, backend.delayed.size(), "the break refresh is deferred, so BREAK fires from live state first");
        assertEquals(1L, backend.delayed.get(0).delayTicks());
    }

    @Test
    void reconcileDoesNothingWhenHandContentIsUnchanged() {
        equip.gear[4] = stack("rage-1", rage());
        listener.refresh(player); // stamps the baseline signature (worn.refresh called once)

        boolean refreshed = listener.reconcile(player);

        assertFalse(refreshed, "identical hand content → no refresh");
        verify(worn, times(1)).refresh(eq(player), any()); // still just the initial refresh — timers not churned
    }

    @Test
    void reconcileRefreshesWhenTheCombatBlobChanges() {
        equip.gear[4] = stack("rage-1", rage());
        listener.refresh(player);

        equip.gear[4] = stack("guard-1", guard()); // a real ability-content change (e.g. crystal extracted)
        boolean refreshed = listener.reconcile(player);

        assertTrue(refreshed, "changed combat state → refresh");
        verify(worn, times(2)).refresh(eq(player), any());
    }

    @Test
    void reconcileIgnoresADurabilityOnlyChange() {
        // Two distinct ItemStacks (different blobs) decoding to the SAME CombatState — a durability tick. The
        // signature is value-hashed over CombatState, so it must NOT drift, or every fighter's REPEATING abilities
        // would restart every 2s sweep.
        equip.gear[4] = stack("rage-dur-100", rage());
        listener.refresh(player);

        equip.gear[4] = stack("rage-dur-99", rage()); // same enchants/crystals/set — only durability moved
        boolean refreshed = listener.reconcile(player);

        assertFalse(refreshed, "durability-only change must not refresh (REPEATING-timer regression guard)");
        verify(worn, times(1)).refresh(eq(player), any());
    }

    @Test
    void quitClearsTheHandSignatureSoTheNextReconcileRefreshes() {
        equip.gear[4] = stack("rage-1", rage());
        listener.refresh(player); // stamps a signature

        PlayerQuitEvent quit = mock(PlayerQuitEvent.class);
        when(quit.getPlayer()).thenReturn(player);
        listener.onQuit(quit); // must forget the signature

        boolean refreshed = listener.reconcile(player); // no prior signature → always refreshes
        assertTrue(refreshed, "after quit the signature is gone, so a rejoin/first reconcile stamps fresh");
    }
}
