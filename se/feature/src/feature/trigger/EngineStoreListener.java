package feature.trigger;

import engine.stores.EngineStores;
import feature.soul.SoulService;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * The single quit-cleanup authority for per-player engine state (§5.4): frees a leaving player's entries in
 * every store the moment they leave. It iterates the {@link EngineStores} aggregate, so a store newly added to
 * the aggregate structurally cannot miss the quit sweep. The stores also self-bound via lazy TTL eviction on
 * read; this is the upper bound. The soul cache is swept separately (it is not player-store-keyed).
 */
public final class EngineStoreListener implements Listener {

    private final EngineStores stores;
    private final SoulService souls;

    public EngineStoreListener(EngineStores stores, SoulService souls) {
        this.stores = Objects.requireNonNull(stores, "stores");
        this.souls = Objects.requireNonNull(souls, "souls");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        stores.clearAll(id);
        souls.evictCache(id); // SoulListener.clear flushes owed drain + mode; this frees the cached total
    }
}
