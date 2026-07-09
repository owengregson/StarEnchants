package feature.trigger;

import engine.stores.EngineStores;
import engine.stores.PlayerScoped;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * The single quit-cleanup authority for per-player engine state (§5.4): frees a leaving player's entries in
 * every store the moment they leave. It iterates the {@link EngineStores} aggregate, so a store newly added to
 * the aggregate structurally cannot miss the quit sweep. The stores also self-bound via lazy TTL eviction on
 * read; this is the upper bound.
 *
 * <p>Module-declared per-player stores are swept here too (ADR-0047), so a feature's quit cleanup is a
 * declaration on its {@code FeatureModule} — not a feature-local {@code PlayerQuitEvent} handler. The fold
 * materializes this listener over {@code EngineStores} plus the concatenation of every module's declared
 * {@code playerStores}: the soul-total cache, the holy death→respawn stash, and the pending nametag capture.
 *
 * <p>The engine aggregate is swept via {@link EngineStores#quitSweep} (NOT a full clear): combat-integrity
 * windows (cooldowns, victim-applied teleblock/suppression) are RETAINED across the relog against the monotonic
 * tick, so a quick disconnect+reconnect cannot skip a cooldown or shed a landed debuff. The module-declared
 * {@code extra} stores keep clearing wholesale — that state is player-private, not combat integrity.
 */
public final class EngineStoreListener implements Listener {

    private final EngineStores stores;
    private final List<PlayerScoped> extra;
    private final LongSupplier nowTicks;

    public EngineStoreListener(EngineStores stores, List<PlayerScoped> extra, LongSupplier nowTicks) {
        this.stores = Objects.requireNonNull(stores, "stores");
        this.extra = List.copyOf(extra);
        this.nowTicks = Objects.requireNonNull(nowTicks, "nowTicks");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        stores.quitSweep(id, nowTicks.getAsLong());
        for (PlayerScoped store : extra) {
            store.clear(id); // module-declared per-player state (souls cache, kept-items stash, nametag capture)
        }
    }
}
