package feature.combat;

import java.util.Objects;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;

/** Maintains Cosmic's Bukkit {@code fromMobSpawner} marker from the authoritative spawn event. */
public final class SpawnerOriginListener implements Listener {

    private final Plugin plugin;

    public SpawnerOriginListener(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER) {
            event.getEntity().setMetadata("fromMobSpawner", new FixedMetadataValue(plugin, true));
        }
    }
}
