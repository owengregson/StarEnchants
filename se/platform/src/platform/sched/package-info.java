/**
 * The Scheduling abstraction (docs/architecture.md §3.5–3.6, §9; {@code folia-scheduling}
 * skill). {@link platform.sched.Scheduling} is the single entry point the whole plugin uses;
 * {@link platform.sched.SchedulerBackend} is the strategy behind it, with a floor-API
 * {@link platform.sched.BukkitSchedulerBackend} for Paper and a reflectively-loaded Folia
 * backend ({@code compat-folia}) for threaded-regions servers. Tasks are owned by entity /
 * region / global / async to match Folia's thread model; on Paper they collapse to the main
 * thread, so one call site is correct on both. {@link platform.sched.TaskHandle} cancels
 * repeating work uniformly across the two native task types.
 */
package platform.sched;
