package engine.sink;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import platform.sched.TaskHandle;

/** Per-boot, per-player non-overlapping regeneration windows (Cosmic Angelic). */
public final class RegenerationWindows {

    public static final class Window {
        private volatile TaskHandle repeating = TaskHandle.CANCELLED;

        private void repeating(TaskHandle handle) {
            this.repeating = handle == null ? TaskHandle.CANCELLED : handle;
        }

        private void cancel() {
            repeating.cancel();
        }
    }

    private final ConcurrentHashMap<UUID, Window> active = new ConcurrentHashMap<>();

    /** Arm a window, or return {@code null} when this player already has one. */
    public Window arm(UUID player) {
        Window created = new Window();
        return active.putIfAbsent(player, created) == null ? created : null;
    }

    public void attach(Window window, TaskHandle repeating) {
        window.repeating(repeating);
    }

    /** Finish only the exact current window; safe against stale expiry callbacks. */
    public void finish(UUID player, Window window) {
        if (active.remove(player, window)) {
            window.cancel();
        }
    }

    public boolean active(UUID player) {
        return active.containsKey(player);
    }
}
