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
 * batches of world-mutation intents that could not run inline, grouped by the thread that <em>owns</em>
 * them and flushed in one pass through {@link Scheduling} after the gate walk, so N hops collapse to ~1
 * per distinct owner (one entity-scheduler task per victim, one region task per chunk).
 *
 * <p>Owners come straight from each intent's target, so even a mis-declared {@code Affinity} can never
 * route an entity mutation off the entity's thread (§3.6): an {@link Entity} intent is owned by that
 * entity, a {@link Location} intent by the region ticking its chunk, the rest by the global region
 * thread. A chunk is the unit of region identity on Folia, so batching by {@code (world, chunkX, chunkZ)}
 * is exactly one hop per region with no Folia internals.
 *
 * <p><strong>WAIT delays (§3.6).</strong> An intent may carry an N-tick delay ({@code WAIT:N} accumulated
 * before its effect). Delay-0 intents are the hot path (immediate batches below). A delay&gt;0 intent is
 * held in a lazily-allocated per-delay sub-plan and flushed N ticks later through the {@code *Later}
 * variants, each owner-batch on its OWN thread — so a delayed mutation is as Folia-correct as an
 * immediate one. Only the mutation is deferred; the effect resolved its targets on the firing thread.
 *
 * <p>Package-private mechanism with no policy: {@link DispatchSink} decides inline-vs-deferred and what
 * each intent does; this only remembers and schedules. Built fresh per event and flushed once.
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

    /**
     * WAIT tiers: delay (ticks, &gt;0) → the sub-plan holding that tier's intents, in first-seen order.
     * Lazily allocated so a WAIT-free event allocates nothing extra. A sub-plan only ever holds immediate
     * batches, never its own delayed tiers (a delayed intent is queued through its delay-0 methods).
     */
    private Map<Integer, DispatchPlan> delayedTiers;

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

    // ── delay-aware queueing (delayTicks <= 0 routes to the immediate batch above) ────────────────

    /** Queue an entity mutation, deferred by {@code delayTicks} (a {@code WAIT} tier) or immediate when &le; 0. */
    void onEntity(Entity entity, Runnable op, int delayTicks) {
        if (delayTicks <= 0) {
            onEntity(entity, op);
        } else {
            tier(delayTicks).onEntity(entity, op);
        }
    }

    /** Queue a region mutation, deferred by {@code delayTicks} (a {@code WAIT} tier) or immediate when &le; 0. */
    void onRegion(Location at, Runnable op, int delayTicks) {
        if (delayTicks <= 0) {
            onRegion(at, op);
        } else {
            tier(delayTicks).onRegion(at, op);
        }
    }

    /** Queue a global mutation, deferred by {@code delayTicks} (a {@code WAIT} tier) or immediate when &le; 0. */
    void onGlobal(Runnable op, int delayTicks) {
        if (delayTicks <= 0) {
            onGlobal(op);
        } else {
            tier(delayTicks).onGlobal(op);
        }
    }

    private DispatchPlan tier(int delayTicks) {
        if (delayedTiers == null) {
            delayedTiers = new LinkedHashMap<>();
        }
        return delayedTiers.computeIfAbsent(delayTicks, d -> new DispatchPlan());
    }

    boolean isEmpty() {
        return entityBatches.isEmpty() && regionBatches.isEmpty()
                && globalBatch.isEmpty() && asyncBatch.isEmpty()
                && (delayedTiers == null || delayedTiers.isEmpty());
    }

    /**
     * Schedule every batch on its owning thread — one hop per distinct owner — running its ops in emission
     * order. Immediate batches dispatch now; each {@code WAIT} tier on a delayed timer of its tick count.
     * Called once, on the firing thread, after the gate walk. A throwing op is isolated so it never aborts
     * the rest of the batch: world mutation is best-effort warn-and-skip (§9).
     */
    void flush() {
        dispatch(0);
        if (delayedTiers != null) {
            for (Map.Entry<Integer, DispatchPlan> tier : delayedTiers.entrySet()) {
                tier.getValue().dispatch(tier.getKey());
            }
        }
    }

    /**
     * Schedule this plan's immediate batches on their owning threads, deferred by {@code delayTicks}
     * (0 = now). The delay applies per owner-batch on that owner's own scheduler, so a delayed entity
     * mutation still rides the entity's region thread (Folia) / main thread (Paper), never a wrong one.
     */
    private void dispatch(int delayTicks) {
        for (Map.Entry<Entity, List<Runnable>> batch : entityBatches.entrySet()) {
            List<Runnable> ops = batch.getValue();
            Entity owner = batch.getKey();
            if (delayTicks <= 0) {
                Scheduling.onEntity(owner, () -> runAll(ops));
            } else {
                Scheduling.onEntityLater(owner, delayTicks, () -> runAll(ops));
            }
        }
        for (RegionBatch batch : regionBatches.values()) {
            List<Runnable> ops = batch.ops;
            if (delayTicks <= 0) {
                Scheduling.onRegion(batch.at, () -> runAll(ops));
            } else {
                Scheduling.onRegionLater(batch.at, delayTicks, () -> runAll(ops));
            }
        }
        if (!globalBatch.isEmpty()) {
            List<Runnable> ops = globalBatch;
            if (delayTicks <= 0) {
                Scheduling.onGlobal(() -> runAll(ops));
            } else {
                Scheduling.onGlobalLater(delayTicks, () -> runAll(ops));
            }
        }
        if (!asyncBatch.isEmpty()) {
            List<Runnable> ops = asyncBatch;
            Scheduling.async(() -> runAll(ops)); // I/O carries no Bukkit state; a tick delay is meaningless
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
