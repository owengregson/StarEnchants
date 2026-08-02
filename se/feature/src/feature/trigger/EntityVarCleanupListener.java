package feature.trigger;

import engine.stores.VarStore;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * Drops a dead MOB's dynamic vars (TARGET_VAR). A mob carrier never fires {@code PlayerQuitEvent}, so the
 * quit sweep that bounds the store for players cannot reach it — without this a world full of bleeding mobs
 * leaks one map entry per carrier until restart.
 *
 * <p>Players are deliberately skipped: their vars survive death exactly as before (the quit sweep still
 * bounds them), so this adds a mob lifecycle without silently changing a shipped player one.
 */
public final class EntityVarCleanupListener implements Listener {

    private final VarStore vars;

    public EntityVarCleanupListener(VarStore vars) {
        this.vars = vars;
    }

    /** MONITOR: the death is decided; this only reclaims memory, so it never influences the outcome. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            vars.clear(event.getEntity().getUniqueId());
        }
    }
}
