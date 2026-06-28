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
 * Applies the {@code KEEP_ON_DEATH} flag (docs/v3-directives.md §C combat-flags): on a player death with a
 * live keep flag, retains the WHOLE inventory + levels and clears drops. Distinct from the holy white scroll
 * ({@link feature.scroll.HolyScrollListener}), which keeps only the single item it was applied to.
 *
 * <p>Runs at {@code NORMAL}, before the holy scroll's {@code HIGH}: setting {@code keepInventory} here trips
 * the scroll listener's {@code getKeepInventory()} guard, so this free always-on enchant takes precedence and
 * a holy scroll is never spent on an already-kept death. The flag is NOT consumed — the worn REPEATING ability
 * re-arms it each tick, so it keeps across deaths while worn.
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
            return; // gamerule or an earlier handler already keeps inventory
        }
        Player player = event.getEntity();
        if (!store.shouldKeep(player.getUniqueId(), nowTicks.getAsLong())) {
            return;
        }
        // keepInventory + cleared drops: the whole inventory is retained, not duplicated.
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setKeepLevel(true);
        event.setDroppedExp(0);
    }
}
