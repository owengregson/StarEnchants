package tester.suite;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import platform.sched.Scheduling;
import tester.fake.FakePlayers;
import tester.harness.Harness;

/**
 * Live checks for the clientless fake-player harness (live-server-testing skill) — the keystone every
 * player-driven suite depends on. Proves a real NMS-backed {@link Player} spawns with no client, is
 * registered server-wide, and survives several ticks on Paper and Folia; the risk is the voided
 * connection channel wedging the tick thread under chunk/tracker/keep-alive sends.
 *
 * <p>Spawn runs on the spawn region's thread, not the global thread: on Folia {@code placeNewPlayer}
 * reads {@code getCurrentWorldData()}, null on the global thread. So the chunk is force-loaded on the
 * global thread first (Folia requires force-load there) to make its region tick, then spawn runs via
 * {@code onRegion}; on Paper {@code onRegion} collapses to the main thread, so one path serves both.
 */
public final class FakePlayerSuite implements Harness.Scenario {

    private final Plugin plugin;

    public FakePlayerSuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        h.expect("fakeplayer.spawnOnline");
        h.expect("fakeplayer.survivesTicks");

        World world = plugin.getServer().getWorlds().get(0);
        Location spawn = world.getSpawnLocation();
        int cx = spawn.getBlockX() >> 4;
        int cz = spawn.getBlockZ() >> 4;

        // Force-load on the global thread (Folia rejects it elsewhere) so the region ticks, then spawn
        // on that region's thread where placeNewPlayer's getCurrentWorldData() is non-null.
        Scheduling.onGlobal(() -> {
            world.setChunkForceLoaded(cx, cz, true);
            Scheduling.onRegion(spawn, () -> {
                Player[] spawned = new Player[1];
                h.guard("fakeplayer.spawnOnline", () -> {
                    Player player = FakePlayers.spawn(world, "se_fake_1");
                    spawned[0] = player;
                    if (!player.isOnline()) {
                        throw new IllegalStateException("fake player is not online after spawn");
                    }
                    if (player.getWorld() == null) {
                        throw new IllegalStateException("fake player has no world after spawn");
                    }
                    boolean listed = plugin.getServer().getOnlinePlayers().stream()
                            .anyMatch(online -> online.getUniqueId().equals(player.getUniqueId()));
                    if (!listed) {
                        throw new IllegalStateException("fake player is not in getOnlinePlayers()");
                    }
                });

                Player player = spawned[0];
                if (player == null) {
                    return; // spawn failed; guard recorded the FAIL, the survival check times out
                }

                // Survive several ticks (chunk/tracker/keep-alive sends all hit the voided channel), then
                // read state on the player's own thread.
                Scheduling.onEntityLater(player, 5L, () -> {
                    h.guard("fakeplayer.survivesTicks", () -> {
                        if (!player.isOnline() || !player.isValid()) {
                            throw new IllegalStateException("fake player vanished within 5 ticks");
                        }
                        player.getLocation();
                        player.getHealth();
                    });
                    FakePlayers.despawn(player);
                    // Do NOT clear the spawn-chunk force-load: it's a shared boolean (not a refcount) that
                    // concurrent combat suites also set, so clearing it would unload a chunk mid-flight.
                });
            });
        });
    }
}
