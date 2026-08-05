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

    /**
     * MONITOR: the death is decided; this only reclaims memory, so it never influences the outcome.
     *
     * <p>A MOB loses everything — it will never be seen again, so nothing it carried can mean anything. A
     * PLAYER loses only what asked to go ({@code SET_VAR clear-on-death}): their vars otherwise survive death
     * on purpose, and a blanket clear here would silently end every mark and window somebody else armed on
     * them. {@code PlayerDeathEvent} extends {@code EntityDeathEvent}, so both arms are this one handler.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        java.util.UUID id = event.getEntity().getUniqueId();
        if (event.getEntity() instanceof Player) {
            vars.clearDeathScoped(id);
        } else {
            vars.clear(id);
        }
    }
}
