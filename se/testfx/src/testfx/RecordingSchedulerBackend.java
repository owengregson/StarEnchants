package testfx;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import platform.sched.SchedulerBackend;
import platform.sched.TaskHandle;

/**
 * Captures {@code *Later} hops for {@link #runDelayed()} assertion and arms {@code repeating*} tasks as
 * cancellable {@link Repeat} handles; immediate hops run inline. (The repeating tasks are recorded rather
 * than run-once — a semantic delta from the old engine-local double that is unobservable, since no unit
 * consumer drives a repeating task through this double.)
 */
public final class RecordingSchedulerBackend implements SchedulerBackend {

    public record Delayed(long delayTicks, Runnable task) {
    }

    /** A recorded repeating task: its initial delay + period + body + own cancellation flag. */
    public static final class Repeat implements TaskHandle {
        public final long initialDelayTicks;
        public final long periodTicks;
        public final Runnable task;
        private boolean cancelled;

        Repeat(long initialDelayTicks, long periodTicks, Runnable task) {
            this.initialDelayTicks = initialDelayTicks;
            this.periodTicks = periodTicks;
            this.task = task;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }

    public final List<Delayed> delayed = new ArrayList<>();
    public final List<Repeat> repeating = new ArrayList<>();

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
        Repeat r = new Repeat(initialDelayTicks, periodTicks, task);
        repeating.add(r);
        return r;
    }

    @Override
    public TaskHandle repeatingRegion(Location location, long initialDelayTicks, long periodTicks, Runnable task) {
        Repeat r = new Repeat(initialDelayTicks, periodTicks, task);
        repeating.add(r);
        return r;
    }

    @Override
    public TaskHandle repeatingGlobal(long initialDelayTicks, long periodTicks, Runnable task) {
        Repeat r = new Repeat(initialDelayTicks, periodTicks, task);
        repeating.add(r);
        return r;
    }

    public void runDelayed() {
        List<Delayed> due = List.copyOf(delayed);
        delayed.clear();
        for (Delayed d : due) {
            d.task().run();
        }
    }
}
