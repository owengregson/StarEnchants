/**
 * The Scheduling abstraction (docs/architecture.md §3.5–3.6; {@code folia-scheduling}).
 * {@link platform.sched.Scheduling} is the single entry point; {@link platform.sched.SchedulerBackend} is
 * the strategy, with a floor-API Paper backend and a reflectively-loaded Folia one. Tasks are owned by
 * entity/region/global/async to match Folia; on Paper they collapse to the main thread, so one call site
 * is correct on both.
 */
package platform.sched;
