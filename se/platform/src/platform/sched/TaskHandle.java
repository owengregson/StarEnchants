package platform.sched;

/**
 * A cancellable handle to a scheduled (usually repeating) task, returned by the repeating
 * methods of {@link SchedulerBackend}/{@link Scheduling}. It hides the platform's native
 * task type (Bukkit's {@code BukkitTask} vs Folia's {@code ScheduledTask}) behind one
 * uniform contract so engine/feature code cancels a timer the same way on both
 * (docs/architecture.md §3.7; {@code folia-scheduling} skill).
 *
 * <p>On Folia a task may stop on its own when its owning entity is removed or its region
 * unloads; {@link #isCancelled()} reflects that as well as an explicit {@link #cancel()}.
 */
public interface TaskHandle {

    /** Cancel the task; idempotent. A task already finished or auto-retired is a no-op. */
    void cancel();

    /** Whether the task has been cancelled or has otherwise stopped running. */
    boolean isCancelled();

    /** A handle for a task that never started (e.g. the entity was already invalid). */
    TaskHandle CANCELLED = new TaskHandle() {
        @Override
        public void cancel() {
            // already cancelled
        }

        @Override
        public boolean isCancelled() {
            return true;
        }
    };
}
