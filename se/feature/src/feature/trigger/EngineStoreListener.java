package feature.trigger;

import engine.stores.KnockbackControlStore;
import engine.stores.SuppressionStore;
import engine.stores.VarStore;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Clears a quitting player's per-player engine store state (docs/architecture.md §5.4) — the central
 * quit hook for the runtime stores whose contract is "cleared on quit": the writable {@link VarStore}
 * ({@code SET_VAR}/{@code INVERT_VAR}), the {@link SuppressionStore} ({@code SUPPRESS}), and the
 * {@link KnockbackControlStore} ({@code KNOCKBACK_CONTROL}). All are otherwise self-bounding via lazy TTL
 * eviction on read, so this is the upper bound that frees a player's entries the moment they leave. Runs
 * on the quit event's thread; the stores are concurrent, so the clear is Folia-safe.
 */
public final class EngineStoreListener implements Listener {

    private final VarStore vars;
    private final SuppressionStore suppression;
    private final KnockbackControlStore knockback;

    public EngineStoreListener(VarStore vars, SuppressionStore suppression, KnockbackControlStore knockback) {
        this.vars = Objects.requireNonNull(vars, "vars");
        this.suppression = Objects.requireNonNull(suppression, "suppression");
        this.knockback = Objects.requireNonNull(knockback, "knockback");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        vars.clear(id);
        suppression.clear(id);
        knockback.clear(id);
    }
}
