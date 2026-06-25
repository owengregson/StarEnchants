package engine.sink;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import platform.sched.SchedulerBackend;
import platform.sched.TaskHandle;

/**
 * A {@link SchedulerBackend} that runs every task inline on the calling thread, so a {@link DispatchSink}'s
 * {@link DispatchSink#flush() flush} is unit-testable without a server. Delays run now too — a
 * single-threaded test has no second thread to hop to.
 */
public final class SyncSchedulerBackend implements SchedulerBackend {

    @Override
    public void onEntity(Entity entity, Runnable task) {
        task.run();
    }

    @Override
    public void onEntityLater(Entity entity, long delayTicks, Runnable task) {
        task.run();
    }

    @Override
    public TaskHandle repeatingEntity(Entity entity, long initialDelayTicks, long periodTicks, Runnable task) {
        task.run();
        return TaskHandle.CANCELLED;
    }

    @Override
    public void onRegion(Location location, Runnable task) {
        task.run();
    }

    @Override
    public void onRegionLater(Location location, long delayTicks, Runnable task) {
        task.run();
    }

    @Override
    public TaskHandle repeatingRegion(Location location, long initialDelayTicks, long periodTicks, Runnable task) {
        task.run();
        return TaskHandle.CANCELLED;
    }

    @Override
    public void onGlobal(Runnable task) {
        task.run();
    }

    @Override
    public void onGlobalLater(long delayTicks, Runnable task) {
        task.run();
    }

    @Override
    public TaskHandle repeatingGlobal(long initialDelayTicks, long periodTicks, Runnable task) {
        task.run();
        return TaskHandle.CANCELLED;
    }

    @Override
    public void async(Runnable task) {
        task.run();
    }
}
