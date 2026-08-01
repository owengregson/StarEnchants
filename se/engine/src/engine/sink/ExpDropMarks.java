package engine.sink;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Per-mob, per-enchant XP multipliers consumed atomically when the marked mob dies. */
public final class ExpDropMarks {

    private ExpDropMarks() {
    }

    private static final Map<UUID, Map<String, Double>> MARKS = new ConcurrentHashMap<>();

    public static void mark(UUID entity, String channel, double multiplier) {
        if (entity == null || channel == null || channel.isBlank() || multiplier <= 0.0) {
            return;
        }
        MARKS.computeIfAbsent(entity, ignored -> new ConcurrentHashMap<>()).put(channel, multiplier);
    }

    /** Remove all marks for an entity and return their product; one means unmarked. */
    public static double consume(UUID entity) {
        Map<String, Double> marks = entity == null ? null : MARKS.remove(entity);
        if (marks == null || marks.isEmpty()) {
            return 1.0;
        }
        double product = 1.0;
        for (double multiplier : marks.values()) {
            product *= multiplier;
        }
        return product;
    }

    public static void clearAll() {
        MARKS.clear();
    }
}
