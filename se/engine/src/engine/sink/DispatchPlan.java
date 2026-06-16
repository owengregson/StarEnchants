package engine.sink;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import platform.sched.Scheduling;

/**
 * The deferred half of the {@link Sink} dispatcher (docs/architecture.md §3.5–3.6): the per-event
 * batches of world-mutation intents that could not run inline, grouped by the thread that
 * <em>owns</em> them, and flushed in one pass through {@link Scheduling} after the gate walk. This
 * is where "N hops collapse to ~1 per distinct thread" happens — every intent for the same victim
 * rides one entity-scheduler task; every intent in the same chunk rides one region task.
 *
 * <p>Owners come straight from each intent's target, so even a mis-declared {@code Affinity} can
 * never route an entity mutation off the entity's thread (§3.6): an {@link Entity} intent is owned
 * by that entity, a {@link Location} intent by the region ticking its chunk, and the rest by the
 * global region thread. A chunk is the unit of region identity on Folia — two locations in the same
 * chunk are guaranteed the same region — so batching by {@code (world, chunkX, chunkZ)} is exactly
 * one hop per region without needing any Folia internals.
 *
 * <p>Package-private mechanism with no policy: {@link DispatchSink} decides inline-vs-deferred and
 * what each intent does; this only remembers and schedules. Built fresh per event (the intents are
 * freshly-allocated closures, so there is no pooled-carrier aliasing hazard to snapshot around) and
 * flushed once.
 */
final class DispatchPlan {

    private static final Logger LOG = System.getLogger("StarEnchants.Sink");

    /** A chunk identity = a Folia region identity: two locations in one chunk share an owner. */
    private record RegionKey(UUID world, int chunkX, int chunkZ) {
    }

    /** A region batch keeps one representative location to schedule on plus its ordered ops. */
    private static final class RegionBatch {
        private final Location at;
        private final List<Runnable> ops = new ArrayList<>();

        private RegionBatch(Location at) {
            this.at = at;
        }
    }

    private final Map<Entity, List<Runnable>> entityBatches = new LinkedHashMap<>();
    private final Map<RegionKey, RegionBatch> regionBatches = new LinkedHashMap<>();
    private final List<Runnable> globalBatch = new ArrayList<>();
    private final List<Runnable> asyncBatch = new ArrayList<>();

    /** Queue a mutation owned by {@code entity} (runs on the entity's region thread on Folia). */
    void onEntity(Entity entity, Runnable op) {
        entityBatches.computeIfAbsent(entity, e -> new ArrayList<>()).add(op);
    }

    /** Queue a mutation owned by the region ticking {@code at}, batched per chunk. */
    void onRegion(Location at, Runnable op) {
        World world = at.getWorld();
        UUID worldId = world == null ? null : world.getUID();
        RegionKey key = new RegionKey(worldId, at.getBlockX() >> 4, at.getBlockZ() >> 4);
        regionBatches.computeIfAbsent(key, k -> new RegionBatch(at)).ops.add(op);
    }

    /** Queue a mutation owned by the global region thread (world-wide / server-wide work). */
    void onGlobal(Runnable op) {
        globalBatch.add(op);
    }

    /** Queue work that touches no Bukkit API (I/O); none of the current intents use this. */
    void onAsync(Runnable op) {
        asyncBatch.add(op);
    }

    boolean isEmpty() {
        return entityBatches.isEmpty() && regionBatches.isEmpty()
                && globalBatch.isEmpty() && asyncBatch.isEmpty();
    }

    /**
     * Schedule every batch on its owning thread — one hop per distinct owner — running each
     * batch's ops in emission order. Called once, on the firing thread, after the gate walk. An op
     * that throws (e.g. its entity went invalid before the hop landed) is isolated so it never
     * aborts the rest of the batch: world mutation is best-effort warn-and-skip (§9).
     */
    void flush() {
        for (Map.Entry<Entity, List<Runnable>> batch : entityBatches.entrySet()) {
            List<Runnable> ops = batch.getValue();
            Scheduling.onEntity(batch.getKey(), () -> runAll(ops));
        }
        for (RegionBatch batch : regionBatches.values()) {
            List<Runnable> ops = batch.ops;
            Scheduling.onRegion(batch.at, () -> runAll(ops));
        }
        if (!globalBatch.isEmpty()) {
            List<Runnable> ops = globalBatch;
            Scheduling.onGlobal(() -> runAll(ops));
        }
        if (!asyncBatch.isEmpty()) {
            List<Runnable> ops = asyncBatch;
            Scheduling.async(() -> runAll(ops));
        }
    }

    private static void runAll(List<Runnable> ops) {
        for (Runnable op : ops) {
            try {
                op.run();
            } catch (Throwable failed) {
                // One intent failing must not sink the batch; log and move on (§9 warn-and-skip).
                LOG.log(Level.WARNING, "intent failed during dispatch flush", failed);
            }
        }
    }
}
