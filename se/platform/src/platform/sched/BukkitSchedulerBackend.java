package platform.sched;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * The floor-API ({@code Bukkit.getScheduler()}) backend for Paper/Spigot, where the four owner flavours
 * of {@link SchedulerBackend} collapse to one main thread. The <em>only</em> place allowed to call
 * {@code Bukkit.getScheduler()} (lint-enforced; docs/architecture.md §3.5). Delays/periods are game ticks.
 */
public final class BukkitSchedulerBackend implements SchedulerBackend {

    private final Plugin plugin;

    public BukkitSchedulerBackend(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEntity(Entity entity, Runnable task) {
        runSync(task);
    }

    @Override
    public void onEntityLater(Entity entity, long delayTicks, Runnable task) {
        runLater(delayTicks, task);
    }

    @Override
    public TaskHandle repeatingEntity(Entity entity, long initialDelayTicks, long periodTicks, Runnable task) {
        return runTimer(initialDelayTicks, periodTicks, task);
    }

    @Override
    public void onRegion(Location location, Runnable task) {
        runSync(task);
    }

    @Override
    public void onRegionLater(Location location, long delayTicks, Runnable task) {
        runLater(delayTicks, task);
    }

    @Override
    public TaskHandle repeatingRegion(Location location, long initialDelayTicks, long periodTicks, Runnable task) {
        return runTimer(initialDelayTicks, periodTicks, task);
    }

    @Override
    public void onGlobal(Runnable task) {
        runSync(task);
    }

    @Override
    public void onGlobalLater(long delayTicks, Runnable task) {
        runLater(delayTicks, task);
    }

    @Override
    public TaskHandle repeatingGlobal(long initialDelayTicks, long periodTicks, Runnable task) {
        return runTimer(initialDelayTicks, periodTicks, task);
    }

    @Override
    public void async(Runnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    private void runSync(Runnable task) {
        // Already on the main thread: run inline (no needless task churn); else hop onto it.
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    private void runLater(long delayTicks, Runnable task) {
        Bukkit.getScheduler().runTaskLater(plugin, task, Math.max(1L, delayTicks));
    }

    private TaskHandle runTimer(long initialDelayTicks, long periodTicks, Runnable task) {
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimer(
                plugin, task, Math.max(0L, initialDelayTicks), Math.max(1L, periodTicks));
        return new BukkitTaskHandle(bukkitTask);
    }

    private static final class BukkitTaskHandle implements TaskHandle {
        private final BukkitTask task;

        BukkitTaskHandle(BukkitTask task) {
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
