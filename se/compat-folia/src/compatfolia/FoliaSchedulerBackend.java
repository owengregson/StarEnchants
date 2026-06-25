package compatfolia;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import platform.sched.SchedulerBackend;
import platform.sched.TaskHandle;

/**
 * The Folia ({@code io.papermc.paper.threadedregions}) implementation of {@link SchedulerBackend}
 * (docs/architecture.md §9; {@code folia-scheduling} skill). Entity work goes through the
 * entity's own scheduler (it follows the entity across regions/teleports/dimensions); region
 * work through the region scheduler for a location; global work through the global region
 * scheduler; async through the async scheduler.
 *
 * <p>This class references Folia-only API and is therefore <em>only ever loaded</em> when
 * {@code Scheduling.init} sees the Folia marker — on Paper it is shaded into the jar but never
 * instantiated, so its Folia references never link. Folia rejects sub-1-tick delays/periods, so
 * delayed and repeating calls clamp to a floor of 1.
 */
public final class FoliaSchedulerBackend implements SchedulerBackend {

    private final Plugin plugin;

    public FoliaSchedulerBackend(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEntity(Entity entity, Runnable task) {
        // null retired-callback: drop the task if the entity is gone before it runs.
        entity.getScheduler().run(plugin, consume(task), null);
    }

    @Override
    public void onEntityLater(Entity entity, long delayTicks, Runnable task) {
        entity.getScheduler().runDelayed(plugin, consume(task), null, atLeastOne(delayTicks));
    }

    @Override
    public TaskHandle repeatingEntity(Entity entity, long initialDelayTicks, long periodTicks, Runnable task) {
        ScheduledTask t = entity.getScheduler().runAtFixedRate(
                plugin, consume(task), null, atLeastOne(initialDelayTicks), atLeastOne(periodTicks));
        return handle(t);
    }

    @Override
    public void onRegion(Location location, Runnable task) {
        Bukkit.getRegionScheduler().execute(plugin, location, task);
    }

    @Override
    public void onRegionLater(Location location, long delayTicks, Runnable task) {
        Bukkit.getRegionScheduler().runDelayed(plugin, location, consume(task), atLeastOne(delayTicks));
    }

    @Override
    public TaskHandle repeatingRegion(Location location, long initialDelayTicks, long periodTicks, Runnable task) {
        ScheduledTask t = Bukkit.getRegionScheduler().runAtFixedRate(
                plugin, location, consume(task), atLeastOne(initialDelayTicks), atLeastOne(periodTicks));
        return handle(t);
    }

    @Override
    public void onGlobal(Runnable task) {
        Bukkit.getGlobalRegionScheduler().execute(plugin, task);
    }

    @Override
    public void onGlobalLater(long delayTicks, Runnable task) {
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, consume(task), atLeastOne(delayTicks));
    }

    @Override
    public TaskHandle repeatingGlobal(long initialDelayTicks, long periodTicks, Runnable task) {
        ScheduledTask t = Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                plugin, consume(task), atLeastOne(initialDelayTicks), atLeastOne(periodTicks));
        return handle(t);
    }

    @Override
    public void async(Runnable task) {
        Bukkit.getAsyncScheduler().runNow(plugin, consume(task));
    }

    /** Adapt a {@code Runnable} to the {@code Consumer<ScheduledTask>} Folia's API expects. */
    private static Consumer<ScheduledTask> consume(Runnable task) {
        return scheduledTask -> task.run();
    }

    /** Folia rejects delays/periods below one tick. */
    private static long atLeastOne(long ticks) {
        return Math.max(1L, ticks);
    }

    /** A null task means the entity/region was already gone; treat it as already cancelled. */
    private static TaskHandle handle(ScheduledTask task) {
        if (task == null) {
            return TaskHandle.CANCELLED;
        }
        return new FoliaTaskHandle(task);
    }

    /** Adapts a Folia {@link ScheduledTask} to the platform-neutral {@link TaskHandle}. */
    private static final class FoliaTaskHandle implements TaskHandle {
        private final ScheduledTask task;

        FoliaTaskHandle(ScheduledTask task) {
            this.task = task;
        }

        @Override
        public void cancel() {
            task.cancel();
        }

        @Override
        public boolean isCancelled() {
            return task.isCancelled();
        }
    }
}
