package feature.combat;

import engine.stores.KeepOnDeathStore;
import java.util.Objects;
import java.util.function.LongSupplier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * Applies the {@code KEEP_ON_DEATH} flag (docs/v3-directives.md §C combat-flags): on a player death, if
 * the {@link KeepOnDeathStore} has a live keep flag for them, retain their items + levels and clear the
 * drops — the same four mutations the holy scroll applies, mirroring {@link feature.scroll.HolyScrollListener}.
 *
 * <p><strong>Coordination with the holy scroll.</strong> This runs at {@link EventPriority#NORMAL},
 * <em>earlier</em> than {@code HolyScrollListener}'s {@code HIGH}: when a KEEP_ON_DEATH enchant saves the
 * death it sets {@code keepInventory}, so the scroll listener's leading {@code getKeepInventory()} guard
 * short-circuits and no scroll is spent — the free always-on enchant takes precedence over the consumable.
 * The same guard here makes the keepInventory gamerule (or any earlier keep) a no-op, so it never
 * double-applies. Unlike the scroll, the flag is NOT consumed: it is re-armed each tick by the worn
 * REPEATING ability, so it naturally keeps across deaths while worn and lapses after unequip.
 *
 * <p>Folia: {@code PlayerDeathEvent} fires on the dying player's own region thread; reading the concurrent
 * store and mutating the event is in-thread.
 */
public final class KeepOnDeathListener implements Listener {

    private final KeepOnDeathStore store;
    private final LongSupplier nowTicks;

    public KeepOnDeathListener(KeepOnDeathStore store, LongSupplier nowTicks) {
        this.store = Objects.requireNonNull(store, "store");
        this.nowTicks = Objects.requireNonNull(nowTicks, "nowTicks");
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onDeath(PlayerDeathEvent event) {
        if (event.getKeepInventory()) {
            return; // the gamerule (or an earlier handler) already keeps inventory — nothing to do
        }
        Player player = event.getEntity();
        if (!store.shouldKeep(player.getUniqueId(), nowTicks.getAsLong())) {
            return; // no live KEEP_ON_DEATH flag — the death proceeds normally
        }
        // keepInventory + cleared drops: the inventory is retained, not duplicated (the drops list is
        // still populated otherwise). Mirrors the holy-scroll apply block exactly.
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setKeepLevel(true);
        event.setDroppedExp(0);
    }
}
