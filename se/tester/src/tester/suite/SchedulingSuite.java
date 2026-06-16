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
        h.expect("sched.repeating");
        h.expect("sched.async");

        Scheduling.onGlobal(() -> h.guard("sched.global", () -> {
            // World time is global-owned state; touching it here must not throw.
            world.getFullTime();
        }));

        Scheduling.onRegion(at, () -> h.guard("sched.region", () ->
                world.getBlockAt(at).getType()));

        // Entity-owned: spawn on the region that owns the location, then schedule ON the entity.
        Scheduling.onRegion(at, () -> {
            ArmorStand stand = (ArmorStand) world.spawnEntity(at, EntityType.ARMOR_STAND);
            stand.setInvisible(true);
            stand.setGravity(false);

            Scheduling.onEntity(stand, () -> h.guard("sched.entity", () -> {
                // Mutating the entity from its own scheduler is the region-correct path.
                stand.setCustomName("se-harness");
                stand.setCustomNameVisible(false);
            }));

            Scheduling.onEntityLater(stand, 2L, () -> {
                h.guard("sched.entityLater", () -> {
                    if (!stand.isValid()) {
                        throw new IllegalStateException("entity invalid before delayed task ran");
                    }
                });
                stand.remove();
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
