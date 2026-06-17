package tester.suite;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import platform.sched.Scheduling;
import tester.fake.FakePlayers;
import tester.harness.Harness;

/**
 * Live checks for the clientless fake-player harness (live-server-testing skill) — the keystone that
 * unlocks every player-driven suite (combat triggers, equipment, GUIs). It proves a real, NMS-backed
 * {@link Player} can be spawned with no client, registered server-wide, and kept alive on a real
 * Paper <em>and</em> Folia server — the headline being that the voided connection channel does not
 * wedge the tick thread (the one genuinely risky NMS edge).
 *
 * <ul>
 *   <li>{@code fakeplayer.spawnOnline} — spawn registers the player: it is online, in a world, and
 *       listed in {@code getOnlinePlayers()}.</li>
 *   <li>{@code fakeplayer.survivesTicks} — the player is still online and valid after several ticks
 *       (the connection's outbound voiding held through chunk/tracker/keep-alive sends), and its
 *       state is readable on its own thread without a wrong-region throw.</li>
 * </ul>
 *
 * <p>Spawn runs on the SPAWN REGION's thread, not the global thread: on Folia {@code placeNewPlayer}
 * reads {@code ServerLevel.getCurrentWorldData()}, which is the data of the region the current thread
 * is ticking and is null on the global thread (Folia's own login flow places a player on its spawn
 * region). So the chunk is force-loaded on the global thread first (Folia requires force-load there)
 * so its region ticks, then the spawn runs via {@code onRegion}; on Paper {@code onRegion} collapses
 * to the main thread, so the one path is correct on both. The survival read runs on the player's own
 * entity thread.
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

        // Force-load the spawn chunk on the GLOBAL thread (Folia rejects force-load off the global
        // region) so its region is ticking, THEN spawn the player on that region's thread, where
        // placeNewPlayer's getCurrentWorldData() is non-null.
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
                    return; // spawn failed; the guard recorded the FAIL, the survival check times out
                }

                // Survive several ticks of real server work (chunk sends, entity tracking, keep-alives
                // all hit the voided channel), then read state on the player's own thread, then clean up.
                Scheduling.onEntityLater(player, 5L, () -> {
                    h.guard("fakeplayer.survivesTicks", () -> {
                        if (!player.isOnline() || !player.isValid()) {
                            throw new IllegalStateException("fake player vanished within 5 ticks");
                        }
                        player.getLocation();
                        player.getHealth();
                    });
                    FakePlayers.despawn(player);
                    // Deliberately do NOT setChunkForceLoaded(cx, cz, false): the spawn chunk is a SHARED
                    // resource that the other combat suites (which launch on the same tick) also force-load,
                    // and force-loading is a boolean flag, not a refcount — clearing it here would unload the
                    // chunk out from under a suite still mid-flight. The run shuts the server down anyway.
                });
            });
        });
    }
}
