package feature.combat;

import compile.load.ContentHolder;
import feature.trigger.EquipChangeDriver;
import feature.trigger.LifecycleDriver;
import feature.trigger.MaxHealthDriver;
import feature.trigger.PassiveEffectDriver;
import feature.trigger.RepeatingDriver;
import feature.trigger.SetMessageDriver;
import item.codec.CombatState;
import item.view.ItemViewCache;
import item.worn.EquipSource;
import item.worn.WornState;
import item.worn.WornStateStore;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import platform.sched.Scheduling;

/**
 * The shared {@link WornStateStore} refresher — keeps each player's {@link WornState} fresh, resolved on an
 * equipment change (NOT per hit, §5.5) on the player's own region thread, and drives the §B equipment-lifecycle
 * mechanisms + maintained passive potion buffs on each refresh (ADR-0036/0044). The join / held-item / respawn /
 * quit lifecycle and the {@link #refresh} pipeline live here (era-neutral); the era-specific armour-change SOURCE
 * is a separate feeder that calls {@link #refresh}: modern hooks Paper's {@code PlayerArmorChangeEvent}
 * ({@code ModernArmourChangeListener}); 1.8 (which lacks it) drives refresh from the per-tick {@code LegacyGearPoll}
 * armour-signature delta (docs/legacy-1.8.9-codeshare-design.md §6).
 *
 * <p><strong>Hand-mutation feeders (G02/G03/F09).</strong> Armour has its own event, but every off-hand / held-item
 * mutation that changes neither a hotbar slot NUMBER nor an armour slot used to leave a stale {@link WornState}
 * serving hit procs and re-applied passive buffs. Closed here by a precise feeder set: era-neutral
 * {@code InventoryCloseEvent} (click-out to a chest, in-place crystal extract, same-slot replace),
 * {@code PlayerDropItemEvent} (Q-drop / hand-off — one item never buffs two players), and
 * {@code PlayerItemBreakEvent} (a shattered weapon/shield sheds its abilities). The modern-only off-hand SWAP and
 * pickup-into-empty-hand feed through {@code ModernHandChangeListener}. The residual catchall for the event-less
 * mutations (/clear, an effect swapping the hand) is {@link #reconcile}, driven by the §B passive sweep against a
 * durability-insensitive {@link CombatState} hand signature.
 */
public final class EquipListener implements Listener {

    private final WornStateStore worn;
    private final ContentHolder content;
    private final RepeatingDriver repeating;
    private final LifecycleDriver lifecycle;
    private final EquipChangeDriver equipChanges;
    private final PassiveEffectDriver passiveEffects;
    private final MaxHealthDriver maxHealth;
    private final SetMessageDriver setMessages;
    private final EquipSource equipSource;
    private final ItemViewCache itemViews;
    // F09 catchall: the durability-insensitive hand signature stamped on each refresh; the sweep re-derives it to
    // catch event-less hand mutations. MUST be value-hashed over CombatState (never ItemStack.hashCode) — a
    // durability tick must not read as a change, or every fighter's REPEATING abilities would restart every sweep.
    private final ConcurrentHashMap<UUID, Integer> handSignatures = new ConcurrentHashMap<>();
    // ADR-0053: a post-refresh seam so a feature whose worn-derived state rides this same re-resolution (the mask
    // illusion) re-derives on the SAME player-thread {@link #refresh} — its exact contract. Instance-composed, so
    // no static state (G2-c); no-op until a module subscribes during enable, when no event has yet fired.
    private volatile Consumer<Player> postRefresh = player -> { };
    // A hook on the hotbar-slot change ALONE, kept separate from postRefresh because refresh() also fires for a
    // chest close, a drop and an armour swap — none of which is a weapon swap. %heldticks% needs the narrow one.
    private volatile Consumer<Player> onHeldChange = player -> { };

    /** Equipment-array index where the hand slots begin; indices below it are the four armour slots. */
    private static final int HAND_SLOTS_FROM = 4;

    public EquipListener(WornStateStore worn, ContentHolder content, RepeatingDriver repeating,
                         LifecycleDriver lifecycle, PassiveEffectDriver passiveEffects, MaxHealthDriver maxHealth,
                         SetMessageDriver setMessages, EquipSource equipSource, ItemViewCache itemViews,
                         EquipChangeDriver equipChanges) {
        this.worn = worn;
        this.content = content;
        this.repeating = repeating;
        this.lifecycle = lifecycle;
        this.equipChanges = equipChanges;
        this.passiveEffects = passiveEffects;
        this.maxHealth = maxHealth;
        this.setMessages = setMessages;
        this.equipSource = equipSource;
        this.itemViews = itemViews;
    }

    /**
     * Register a hook run at the END of every {@link #refresh}, on the player's own region thread (ADR-0053).
     * Composes, so several features can ride one refresh; subscribed once during enable (before any event fires).
     */
    public void onRefresh(Consumer<Player> hook) {
        Objects.requireNonNull(hook, "hook");
        this.postRefresh = this.postRefresh.andThen(hook);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        refresh(event.getPlayer());
    }

    /**
     * Register a hook run on a hotbar-slot change ONLY, on the firing thread ({@code %heldticks%}'s stamp).
     * Composes like {@link #onRefresh}; subscribed once during enable, before any event fires.
     */
    public void onHeldChange(Consumer<Player> hook) {
        Objects.requireNonNull(hook, "hook");
        this.onHeldChange = this.onHeldChange.andThen(hook);
    }

    @EventHandler
    public void onHeldChange(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        onHeldChange.accept(player); // stamped NOW, not next tick — the swap happened at this event, not at the refresh
        // The new slot is current only after this event returns — refresh next tick on the player's thread.
        Scheduling.onEntityLater(player, 1L, () -> refresh(player));
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        // Death clears all potion effects; re-derive once respawned so a permanent passive buff returns at once.
        Scheduling.onEntityLater(player, 1L, () -> refresh(player));
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        // Every inventory-screen hand mutation (off-hand click-out, in-place crystal extract, same-slot replace)
        // lands before the screen can close; a modern client cannot fight until then, so this refresh beats any hit.
        if (event.getPlayer() instanceof Player player) {
            Scheduling.onEntityLater(player, 1L, () -> refresh(player));
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        // Shed a dropped/handed-off item's abilities the moment it leaves — one item never buffs two holders (G02).
        Player player = event.getPlayer();
        Scheduling.onEntityLater(player, 1L, () -> refresh(player));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemBreak(PlayerItemBreakEvent event) {
        // Deferred one tick ON PURPOSE: TriggerListeners fires the item's own BREAK abilities from the still-stale
        // state THIS tick; the torn-down hit procs / repeating timers / passive buffs then diff against fresh state
        // next tick (G03). Refresh authority stays here — TriggerListeners is a pure event→trigger map.
        Player player = event.getPlayer();
        Scheduling.onEntityLater(player, 1L, () -> refresh(player));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        worn.remove(event.getPlayer().getUniqueId());
        repeating.disarm(event.getPlayer().getUniqueId());
        lifecycle.clear(event.getPlayer().getUniqueId());
        passiveEffects.clear(event.getPlayer().getUniqueId());
        setMessages.clear(event.getPlayer().getUniqueId());
        equipChanges.clear(event.getPlayer().getUniqueId());
        handSignatures.remove(event.getPlayer().getUniqueId());
    }

    /** Re-resolve {@code player}'s worn state and drive every equipment-lifecycle mechanism from it. Called by
     *  the shared lifecycle handlers and by the era armour-/hand-change feeders (modern events / legacy poll). */
    public void refresh(Player player) {
        WornState state = worn.refresh(player, content.snapshot());
        repeating.arm(player, state);       // (re)arm REPEATING abilities (§B)
        lifecycle.refresh(player, state);   // START/STOP newly-(un)worn HELD/PASSIVE buffs (§B)
        setMessages.refresh(player, state); // §6.6 announce a set becoming complete / dropping below threshold
        passiveEffects.refresh(player);     // reconcile maintained passive potion buffs LAST — it is the authority
        maxHealth.refresh(player);          // reconcile the worn max-health modifier (its own channel — same rule)
        handSignatures.put(player.getUniqueId(), handSignature(player)); // stamp the F09 catchall baseline
        postRefresh.accept(player); // ADR-0053: e.g. the mask illusion re-derive, on this player's own thread
        // EQUIP_CHANGE fires LAST, off the same worn diff: its effects are authored one-shots, so they must see
        // fully-settled state rather than race the maintained-buff reconciliation above. A refresh that changed
        // no EQUIP_CHANGE ability (a chest close, a Q-drop, a durability tick) diffs to nothing and fires nothing.
        equipChanges.refresh(player);
    }

    /**
     * F09 catchall for event-less hand mutations (/clear, an effect swapping the hand): recompute the hand
     * signature and, if it drifted from the last {@link #refresh}, re-resolve. Returns whether it refreshed, so the
     * sweep can skip its own passive re-derive on a drift ({@link #refresh} already did it). No drift → no refresh,
     * so an actively-wearing player's REPEATING abilities are NOT restarted every sweep.
     */
    public boolean reconcile(Player player) {
        Integer prior = handSignatures.get(player.getUniqueId());
        if (prior != null && prior == handSignature(player)) {
            return false;
        }
        refresh(player);
        return true;
    }

    /** A durability-insensitive value hash over the hand slots' combat state — the change signal for {@link #reconcile}. */
    private int handSignature(Player player) {
        ItemStack[] gear = equipSource.snapshot(player);
        if (gear == null) {
            return 0;
        }
        int sig = 1;
        for (int slot = HAND_SLOTS_FROM; slot < gear.length; slot++) {
            ItemStack piece = gear[slot];
            CombatState combat = piece == null ? CombatState.EMPTY : itemViews.of(piece).combat();
            sig = 31 * sig + combat.hashCode(); // CombatState is a record: value hash, and durability is not a field
        }
        return sig;
    }
}
