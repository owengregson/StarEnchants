package platform.sched;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

/**
 * The platform-specific scheduling strategy behind {@link Scheduling}. There are exactly two
 * implementations: the floor-API Bukkit backend (this module) and the Folia threaded-regions
 * backend ({@code compat-folia}, loaded reflectively only when the Folia marker is present).
 * Everything else in the plugin schedules through the {@link Scheduling} facade and never
 * names a backend (docs/architecture.md §3.5–3.6, §9; {@code folia-scheduling} skill).
 *
 * <p>The four <em>owners</em> mirror Folia's thread model (entity / region / global / async).
 * On Paper they all collapse to the single main thread, so the same call is correct on both.
 * Entity-owned work follows the entity across regions, teleports, and dimensions; region work
 * runs on the thread that ticks a given location; global work runs on the global region thread
 * (world time, weather, server-wide timers); async work must touch no Bukkit API.
 */
public interface SchedulerBackend {

    // ── Entity-owned (the common case: anything done TO an entity) ──────────────────────────

    /** Run on the entity's owning thread as soon as possible. */
    void onEntity(Entity entity, Runnable task);

    /** Run on the entity's owning thread after {@code delayTicks} game ticks. */
    void onEntityLater(Entity entity, long delayTicks, Runnable task);

    /** Repeat on the entity's owning thread; cancel via the returned handle. */
    TaskHandle repeatingEntity(Entity entity, long initialDelayTicks, long periodTicks, Runnable task);

    // ── Region-owned (a location/chunk with no entity in hand) ───────────────────────────────

    /** Run on the thread that ticks {@code location} as soon as possible. */
    void onRegion(Location location, Runnable task);

    /** Run on the thread that ticks {@code location} after {@code delayTicks} game ticks. */
    void onRegionLater(Location location, long delayTicks, Runnable task);

    /** Repeat on the thread that ticks {@code location}; cancel via the returned handle. */
    TaskHandle repeatingRegion(Location location, long initialDelayTicks, long periodTicks, Runnable task);

    // ── Global-owned (world time, weather, server-wide timers) ───────────────────────────────

    /** Run on the global region thread as soon as possible. */
    void onGlobal(Runnable task);

    /** Run on the global region thread after {@code delayTicks} game ticks. */
    void onGlobalLater(long delayTicks, Runnable task);

    /** Repeat on the global region thread; cancel via the returned handle. */
    TaskHandle repeatingGlobal(long initialDelayTicks, long periodTicks, Runnable task);

    // ── Async (I/O; must NOT touch the Bukkit API) ───────────────────────────────────────────

    /** Run off any game thread; the task must touch no Bukkit API. */
    void async(Runnable task);
}
