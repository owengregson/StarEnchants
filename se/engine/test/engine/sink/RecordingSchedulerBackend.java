package engine.sink;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import platform.sched.SchedulerBackend;
import platform.sched.TaskHandle;

/** Captures {@code *Later} hops with their delay for assertion via {@link #runDelayed()}; runs everything else inline. */
public final class RecordingSchedulerBackend implements SchedulerBackend {

    public record Delayed(long delayTicks, Runnable task) {
    }

    public final List<Delayed> delayed = new ArrayList<>();

    @Override
    public void onEntity(Entity entity, Runnable task) {
        task.run();
    }

    @Override
    public void onRegion(Location location, Runnable task) {
        task.run();
    }

    @Override
    public void onGlobal(Runnable task) {
        task.run();
    }

    @Override
    public void async(Runnable task) {
        task.run();
    }

    @Override
    public void onEntityLater(Entity entity, long delayTicks, Runnable task) {
        delayed.add(new Delayed(delayTicks, task));
    }

    @Override
    public void onRegionLater(Location location, long delayTicks, Runnable task) {
        delayed.add(new Delayed(delayTicks, task));
    }

    @Override
    public void onGlobalLater(long delayTicks, Runnable task) {
        delayed.add(new Delayed(delayTicks, task));
    }

    @Override
    public TaskHandle repeatingEntity(Entity entity, long initialDelayTicks, long periodTicks, Runnable task) {
        task.run();
        return TaskHandle.CANCELLED;
    }

    @Override
    public TaskHandle repeatingRegion(Location location, long initialDelayTicks, long periodTicks, Runnable task) {
        task.run();
        return TaskHandle.CANCELLED;
    }

    @Override
    public TaskHandle repeatingGlobal(long initialDelayTicks, long periodTicks, Runnable task) {
        task.run();
        return TaskHandle.CANCELLED;
    }

    public void runDelayed() {
        List<Delayed> due = List.copyOf(delayed);
        delayed.clear();
        for (Delayed d : due) {
            d.task().run();
        }
    }
}
