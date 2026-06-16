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
 * Live checks for the {@code Scheduling} abstraction — the heart of the Folia-correctness
 * invariant. Each owner flavour is exercised on a REAL server, and every scheduled body that
 * touches the world/entity is wrapped in {@link Harness#guard}: on Folia a wrong-region or
 * wrong-thread access throws {@code IllegalStateException}, so an incorrectly-routed task surfaces
 * as a recorded FAIL rather than a silent stall. A green Paper run proves nothing here — the same
 * suite must pass on Folia (matrix-gate, folia-scheduling skills).
 *
 * <ul>
 *   <li>{@code sched.global} — a global task runs.</li>
 *   <li>{@code sched.region} — a region task may read the block at its location (region-owned).</li>
 *   <li>{@code sched.entity} — an entity task may MUTATE its entity (entity-owned, the common case).</li>
 *   <li>{@code sched.entityLater} — a delayed entity task actually fires after its delay.</li>
 *   <li>{@code sched.repeating} — a repeating task fires N times and {@code cancel()} stops it.</li>
 *   <li>{@code sched.async} — an async task runs OFF the primary thread.</li>
 * </ul>
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
            // World time is global-owned state; touching it here must not throw.
            world.getFullTime();
        }));

        // A delayed GLOBAL task must fire and may touch global-owned state — the primitive WAIT uses for
        // deferred global/economy effects (the Folia global-region delayed scheduler, untested elsewhere).
        Scheduling.onGlobalLater(2L, () -> h.guard("sched.globalLater", world::getFullTime));

        Scheduling.onRegion(at, () -> h.guard("sched.region", () ->
                world.getBlockAt(at).getType()));

        // Entity-owned: the common case (anything done TO an entity). With no players online,
        // newer servers (26.1.x) cull a freshly-spawned entity within a couple of ticks once its
        // chunk stops entity-ticking, so the delayed-task check would race culling rather than test
        // scheduling. We force-load the chunk to keep it ticking — but on Folia force-load modifies
        // a GLOBAL set ("Cannot modify force loaded chunks off of the global region"), so it must
        // run on the global thread. So: force-load on global, THEN spawn on the location's region,
        // THEN schedule on the entity — each step on its correct owning thread.
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
                    // Mutating the entity from its own scheduler is the region-correct path.
                    stand.setCustomName("se-harness");
                    stand.setCustomNameVisible(false);
                }));

                Scheduling.onEntityLater(stand, 2L, () -> {
                    // The contract is "the delayed ENTITY task fires" — reaching this body proves it.
                    // We deliberately do NOT assert the stand is still valid: a playerless entity can be
                    // culled under tick-stall (the slow floor server under matrix load), yet on Paper the
                    // delayed task still fires, which is exactly what this check exists to verify. Asserting
                    // validity tested an incidental, load-sensitive condition, not scheduling.
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
