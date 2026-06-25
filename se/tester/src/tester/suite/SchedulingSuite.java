package tester.suite;

import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.Plugin;
import platform.sched.Scheduling;
import platform.sched.TaskHandle;
import tester.harness.Harness;

/**
 * The {@code Scheduling} abstraction, live — the heart of the Folia-correctness invariant. Each owner
 * flavour runs on a REAL server; every body touching world/entity is wrapped in {@link Harness#guard}, so
 * a wrong-region/wrong-thread access (which throws on Folia) surfaces as a recorded FAIL, not a silent
 * stall. A green Paper run proves nothing here — the same suite must pass on Folia (matrix-gate,
 * folia-scheduling skills).
 */
public final class SchedulingSuite implements Harness.Scenario {

    private final Plugin plugin;

    public SchedulingSuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        World world = plugin.getServer().getWorlds().get(0);
        Location at = world.getSpawnLocation();

        h.expect("sched.global");
        h.expect("sched.region");
        h.expect("sched.entity");
        h.expect("sched.entityLater");
        h.expect("sched.globalLater");
        h.expect("sched.repeating");
        h.expect("sched.async");

        Scheduling.onGlobal(() -> h.guard("sched.global", () -> {
            world.getFullTime(); // global-owned state; must not throw here
        }));

        // The primitive WAIT uses for deferred global/economy effects — the Folia global-region delayed
        // scheduler, untested elsewhere.
        Scheduling.onGlobalLater(2L, () -> h.guard("sched.globalLater", world::getFullTime));

        Scheduling.onRegion(at, () -> h.guard("sched.region", () ->
                world.getBlockAt(at).getType()));

        // With no players online, 26.1.x culls a freshly-spawned entity within a couple of ticks once its
        // chunk stops entity-ticking, so the delayed check would race culling. Force-load keeps it ticking,
        // but on Folia force-load modifies a GLOBAL set ("Cannot modify force loaded chunks off of the
        // global region"), so it must run on the global thread: force-load on global, THEN spawn on the
        // location's region, THEN schedule on the entity — each step on its owning thread.
        int cx = at.getBlockX() >> 4;
        int cz = at.getBlockZ() >> 4;
        Scheduling.onGlobal(() -> {
            world.setChunkForceLoaded(cx, cz, true);
            Scheduling.onRegion(at, () -> {
                ArmorStand stand = (ArmorStand) world.spawnEntity(at, EntityType.ARMOR_STAND);
                stand.setInvisible(true);
                stand.setGravity(false);
                stand.setPersistent(true);

                Scheduling.onEntity(stand, () -> h.guard("sched.entity", () -> {
                    stand.setCustomName("se-harness"); // mutate from the entity's own scheduler
                    stand.setCustomNameVisible(false);
                }));

                Scheduling.onEntityLater(stand, 2L, () -> {
                    // The contract is just "the delayed ENTITY task fires" — reaching this body proves it.
                    // Do NOT assert the stand is still valid: a playerless entity can be culled under
                    // tick-stall, yet the delayed task still fires, which is what this check verifies.
                    h.guard("sched.entityLater", () -> { /* fired on the entity scheduler — pass */ });
                    stand.remove();
                    Scheduling.onGlobal(() -> world.setChunkForceLoaded(cx, cz, false));
                });
            });
        });

        // Repeating: count to three, then cancel; a fourth firing is a cancellation bug.
        AtomicInteger count = new AtomicInteger();
        TaskHandle[] handle = new TaskHandle[1];
        handle[0] = Scheduling.repeatingGlobal(1L, 1L, () -> {
            int n = count.incrementAndGet();
            if (n == 3) {
                handle[0].cancel();
                h.pass("sched.repeating");
            } else if (n > 3) {
                h.fail("sched.repeating", "fired " + n + " times after cancel()");
            }
        });

        Scheduling.async(() -> h.guard("sched.async", () -> {
            if (Bukkit.isPrimaryThread()) {
                throw new IllegalStateException("async task ran on the primary thread");
            }
        }));
    }
}
