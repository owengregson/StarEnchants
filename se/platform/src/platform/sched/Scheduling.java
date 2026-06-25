package platform.sched;

import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import platform.caps.Capabilities;

/**
 * The single scheduling entry point for the whole plugin (docs/architecture.md §3.5–3.6;
 * {@code folia-scheduling}). Because the door to {@code Bukkit.getScheduler()} is <em>removed</em> rather
 * than discouraged, an effect author cannot write a Folia bug. {@link #init} chooses the backend once at
 * boot; the Folia backend is instantiated reflectively, so its Folia-API-referencing class is never
 * <em>linked</em> on Paper and per-call dispatch stays a plain virtual call.
 */
public final class Scheduling {

    static final String FOLIA_BACKEND = "compatfolia.FoliaSchedulerBackend";

    private static volatile SchedulerBackend backend;

    private Scheduling() {
    }

    /** Call once at enable, before anything schedules. A later call swaps the backend (tests). */
    public static void init(Plugin plugin, Capabilities capabilities) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(capabilities, "capabilities");
        backend = capabilities.folia() ? loadFoliaBackend(plugin) : new BukkitSchedulerBackend(plugin);
    }

    /** Install an explicit backend (tests / embedding). */
    public static void install(SchedulerBackend backend) {
        Scheduling.backend = Objects.requireNonNull(backend, "backend");
    }

    /** Whether a backend has been installed (i.e. {@link #init} has run). */
    public static boolean isInitialized() {
        return backend != null;
    }

    /** The active backend; throws if scheduling is used before {@link #init}. */
    public static SchedulerBackend backend() {
        SchedulerBackend b = backend;
        if (b == null) {
            throw new IllegalStateException("Scheduling.init(plugin, capabilities) must run before scheduling");
        }
        return b;
    }

    private static SchedulerBackend loadFoliaBackend(Plugin plugin) {
        try {
            Class<?> type = Class.forName(FOLIA_BACKEND);
            return (SchedulerBackend) type.getConstructor(Plugin.class).newInstance(plugin);
        } catch (ReflectiveOperationException | LinkageError e) {
            // Folia detected but backend not constructible: a packaging bug (jar missing compat-folia), not recoverable.
            throw new IllegalStateException("Folia detected but " + FOLIA_BACKEND + " is unavailable", e);
        }
    }

    public static void onEntity(Entity entity, Runnable task) {
        backend().onEntity(entity, task);
    }

    public static void onEntityLater(Entity entity, long delayTicks, Runnable task) {
        backend().onEntityLater(entity, delayTicks, task);
    }

    public static TaskHandle repeatingEntity(Entity entity, long initialDelayTicks, long periodTicks, Runnable task) {
        return backend().repeatingEntity(entity, initialDelayTicks, periodTicks, task);
    }

    public static void onRegion(Location location, Runnable task) {
        backend().onRegion(location, task);
    }

    public static void onRegionLater(Location location, long delayTicks, Runnable task) {
        backend().onRegionLater(location, delayTicks, task);
    }

    public static TaskHandle repeatingRegion(Location location, long initialDelayTicks, long periodTicks, Runnable task) {
        return backend().repeatingRegion(location, initialDelayTicks, periodTicks, task);
    }

    public static void onGlobal(Runnable task) {
        backend().onGlobal(task);
    }

    public static void onGlobalLater(long delayTicks, Runnable task) {
        backend().onGlobalLater(delayTicks, task);
    }

    public static TaskHandle repeatingGlobal(long initialDelayTicks, long periodTicks, Runnable task) {
        return backend().repeatingGlobal(initialDelayTicks, periodTicks, task);
    }

    public static void async(Runnable task) {
        backend().async(task);
    }
}
