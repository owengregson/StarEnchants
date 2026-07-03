package feature.trigger;

import engine.stores.EngineStores;
import engine.stores.PlayerScoped;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
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
 */
public final class EngineStoreListener implements Listener {

    private final EngineStores stores;
    private final List<PlayerScoped> extra;

    public EngineStoreListener(EngineStores stores, List<PlayerScoped> extra) {
        this.stores = Objects.requireNonNull(stores, "stores");
        this.extra = List.copyOf(extra);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        stores.clearAll(id);
        for (PlayerScoped store : extra) {
            store.clear(id); // module-declared per-player state (souls cache, kept-items stash, nametag capture)
        }
    }
}
