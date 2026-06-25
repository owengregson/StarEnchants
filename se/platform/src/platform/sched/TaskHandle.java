package platform.sched;

/**
 * A cancellable handle to a scheduled task, hiding the native task type (Bukkit's {@code BukkitTask} vs
 * Folia's {@code ScheduledTask}) behind one contract (docs/architecture.md §3.7; {@code folia-scheduling}).
 * On Folia a task may stop on its own when its owning entity is removed or its region unloads;
 * {@link #isCancelled()} reflects that as well as an explicit {@link #cancel()}.
 */
public interface TaskHandle {

    /** Cancel the task; idempotent. */
    void cancel();

    boolean isCancelled();

    /** A handle for a task that never started (e.g. the entity was already invalid). */
    TaskHandle CANCELLED = new TaskHandle() {
        @Override
        public void cancel() {
        }

        @Override
        public boolean isCancelled() {
            return true;
        }
    };
}
