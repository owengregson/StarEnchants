package feature.combat;

import engine.sink.ExpDropMarks;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

/** Applies and consumes Inquisitive-style mob XP multipliers at the source event priority. */
public final class ExpDropMarkListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent event) {
        double multiplier = ExpDropMarks.consume(event.getEntity().getUniqueId());
        if (multiplier != 1.0) {
            event.setDroppedExp((int) (event.getDroppedExp() * multiplier));
        }
    }
}
