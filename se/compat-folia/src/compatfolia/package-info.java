/**
 * The Folia half of the scheduling abstraction (docs/architecture.md §9; {@code folia-scheduling}):
 * {@link compatfolia.FoliaSchedulerBackend} over {@code io.papermc.paper.threadedregions}, loaded
 * reflectively by {@code platform.sched.Scheduling} only when the Folia marker is present.
 */
package compatfolia;
