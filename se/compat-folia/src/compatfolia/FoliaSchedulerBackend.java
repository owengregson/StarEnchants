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
 * The Folia ({@code io.papermc.paper.threadedregions}) {@link SchedulerBackend} (docs/architecture.md §9;
 * {@code folia-scheduling}). References Folia-only API, so it is loaded ONLY when {@code Scheduling.init} sees
 * the Folia marker — on Paper it is shaded inert and its Folia references never link.
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
