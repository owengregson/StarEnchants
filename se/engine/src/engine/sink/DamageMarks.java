package engine.sink;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-(victim, marker) damage marks: a marker deals extra outgoing damage to a victim they have marked, for a
 * window (reaper's Mark of the Reaper — +25% from the reaper while the mark lasts). Written by the {@code MARK}
 * effect, consulted by {@code CombatDispatch} on the attack side BEFORE the attacker's abilities run (so the
 * marking hit itself is excluded). Static + era-agnostic (no store threading); wall-clock expiry, self-evicting.
 */
public final class DamageMarks {

    private DamageMarks() {
    }

    private record Mark(double fraction, long expiryMs) {
    }

    private static final Map<UUID, Map<UUID, Mark>> MARKS = new ConcurrentHashMap<>();

    /** Mark {@code victim} for {@code marker}: an extra {@code fraction} outgoing (0.25 = +25%) for {@code durationMs}. */
    public static void mark(UUID victim, UUID marker, double fraction, long durationMs) {
        if (victim == null || marker == null || fraction == 0.0 || durationMs <= 0) {
            return;
        }
        MARKS.computeIfAbsent(victim, k -> new ConcurrentHashMap<>())
                .put(marker, new Mark(fraction, System.currentTimeMillis() + durationMs));
    }

    /** The extra outgoing fraction {@code marker} deals to {@code victim} right now (0 if unmarked / expired). */
    public static double bonus(UUID victim, UUID marker) {
        Map<UUID, Mark> byMarker = MARKS.get(victim);
        if (byMarker == null) {
            return 0.0;
        }
        Mark mark = byMarker.get(marker);
        if (mark == null) {
            return 0.0;
        }
        if (System.currentTimeMillis() >= mark.expiryMs()) {
            byMarker.remove(marker, mark);
            return 0.0;
        }
        return mark.fraction();
    }

    /**
     * Every victim {@code marker} currently has an active (non-expired) mark on — the reverse of {@link #bonus}.
     * Drives reaper's {@code @Marked} selector (the continuous tether redraws to each still-marked victim every
     * 0.5s). Scans the small marks table, evicting expired entries as it passes them; never null.
     */
    public static Set<UUID> marked(UUID marker) {
        Set<UUID> out = new HashSet<>();
        if (marker == null) {
            return out;
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Map<UUID, Mark>> entry : MARKS.entrySet()) {
            Mark mark = entry.getValue().get(marker);
            if (mark == null) {
                continue;
            }
            if (now >= mark.expiryMs()) {
                entry.getValue().remove(marker, mark);
            } else {
                out.add(entry.getKey());
            }
        }
        return out;
    }

    /** Forget one victim's marks (quit). */
    public static void clear(UUID victim) {
        MARKS.remove(victim);
    }

    /** Forget all marks (disable). */
    public static void clearAll() {
        MARKS.clear();
    }
}
