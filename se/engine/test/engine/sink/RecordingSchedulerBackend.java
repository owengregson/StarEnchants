package engine.sink;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import platform.sched.SchedulerBackend;
import platform.sched.TaskHandle;

/**
 * A {@link SchedulerBackend} for WAIT tests: immediate hops run inline, but every {@code *Later} hop is
 * captured with its requested delay rather than run — so a test can assert a {@code WAIT} tier scheduled at
 * the right tick, then fire it via {@link #runDelayed()} to prove the batch neither ran early nor was lost.
 * Repeating hops run inline (no WAIT test needs a repeating timer).
 */
public final class RecordingSchedulerBackend implements SchedulerBackend {

    /** One captured delayed batch: the requested delay (ticks) and the batch runnable. */
    public record Delayed(long delayTicks, Runnable task) {
    }

    /** Delayed hops captured in schedule order, awaiting {@link #runDelayed()}. */
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

    /** Fire every captured delayed batch, in schedule order, then clear the queue. */
    public void runDelayed() {
        List<Delayed> due = List.copyOf(delayed);
        delayed.clear();
        for (Delayed d : due) {
            d.task().run();
        }
    }
}
