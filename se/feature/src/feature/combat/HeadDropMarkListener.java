package feature.combat;

import engine.sink.HeadDropMarks;
import engine.sink.SinkEnv;
import engine.sink.SinkFactory;
import engine.sink.SinkReadback;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Consumes Cosmic Headless/Decapitation hit marks on death and emits one head for each enchant channel. */
public final class HeadDropMarkListener implements Listener {

    private final SinkFactory sinks;
    private final SinkEnv env;

    public HeadDropMarkListener(SinkFactory sinks, SinkEnv env) {
        this.sinks = Objects.requireNonNull(sinks, "sinks");
        this.env = Objects.requireNonNull(env, "env");
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        int drops = HeadDropMarks.consume(victim.getUniqueId());
        if (drops <= 0) {
            return;
        }
        SinkReadback sink = sinks.create(env);
        for (int i = 0; i < drops; i++) {
            sink.dropHead(victim, victim.getKiller());
        }
        sink.flush();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        HeadDropMarks.consume(event.getPlayer().getUniqueId());
    }

    public void stop() {
        HeadDropMarks.clearAll();
    }
}
