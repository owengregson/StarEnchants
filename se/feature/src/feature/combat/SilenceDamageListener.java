package feature.combat;

import engine.sink.SinkEnv;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

/** Cosmic Silence's LOW-priority compensation: a silenced player takes exactly 25% less damage. */
public final class SilenceDamageListener implements Listener {

    private final SinkEnv env;

    public SilenceDamageListener(SinkEnv env) {
        this.env = Objects.requireNonNull(env, "env");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getDamage() > 0.0 && event.getEntity() instanceof Player player
                && env.stores().suppression().defenseSuppressed(
                        player.getUniqueId(), env.nowTicks().getAsLong())) {
            event.setDamage(event.getDamage() * 0.75);
        }
    }
}
