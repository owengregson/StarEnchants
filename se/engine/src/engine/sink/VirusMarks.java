package engine.sink;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Timed per-entity multiplier for subsequent Poison/Wither damage. */
public final class VirusMarks {

    private VirusMarks() {
    }

    private record Mark(double multiplier, long expiryMs) {
    }

    private static final Map<UUID, Mark> MARKS = new ConcurrentHashMap<>();

    public static void mark(UUID entity, double multiplier, long durationMs) {
        if (entity != null && multiplier > 0.0 && durationMs > 0) {
            MARKS.put(entity, new Mark(multiplier, System.currentTimeMillis() + durationMs));
        }
    }

    public static double multiplier(UUID entity) {
        Mark mark = entity == null ? null : MARKS.get(entity);
        if (mark == null) {
            return 1.0;
        }
        if (System.currentTimeMillis() >= mark.expiryMs()) {
            MARKS.remove(entity, mark);
            return 1.0;
        }
        return mark.multiplier();
    }

    public static void clear(UUID entity) {
        MARKS.remove(entity);
    }

    public static void clearAll() {
        MARKS.clear();
    }
}
