/**
 * The Folia half of the scheduling abstraction (docs/architecture.md §9; {@code folia-scheduling}
 * skill): {@link compatfolia.FoliaSchedulerBackend} implements {@code platform.sched.SchedulerBackend}
 * against the {@code io.papermc.paper.threadedregions} API. It is loaded reflectively by
 * {@code platform.sched.Scheduling} only when the Folia marker class is present, so the floor jar
 * carries it inertly on Paper and links its Folia references only on a real threaded-regions server.
 */
package compatfolia;
