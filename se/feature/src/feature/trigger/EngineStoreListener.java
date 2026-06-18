package feature.trigger;

import engine.stores.VarStore;
import java.util.Objects;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Clears a quitting player's per-player engine store state (docs/architecture.md §5.4) — the central
 * quit hook for the runtime stores whose contract is "cleared on quit". Today that is the writable
 * {@link VarStore} ({@code SET_VAR}/{@code INVERT_VAR}); the stores are otherwise self-bounding via lazy
 * TTL eviction on read, so this is the upper bound that frees a player's slot the moment they leave.
 * Runs on the quit event's thread; the stores are concurrent, so the clear is Folia-safe.
 */
public final class EngineStoreListener implements Listener {

    private final VarStore vars;

    public EngineStoreListener(VarStore vars) {
        this.vars = Objects.requireNonNull(vars, "vars");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        vars.clear(event.getPlayer().getUniqueId());
    }
}
