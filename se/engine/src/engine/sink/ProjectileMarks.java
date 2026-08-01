package engine.sink;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Per-boot launch-to-impact integer marks for projectile mechanics. */
public final class ProjectileMarks {

    private record Mark(int value, long expiry) {
    }

    private final ConcurrentHashMap<UUID, Mark> marks = new ConcurrentHashMap<>();

    public void mark(UUID projectile, int value, long nowTicks, int ttlTicks) {
        if (projectile != null && value > 0) {
            marks.put(projectile, new Mark(value, nowTicks + Math.max(1, ttlTicks)));
        }
    }

    /** Consume a mark at entity impact; elapsed marks read as zero. */
    public int consume(UUID projectile, long nowTicks) {
        Mark mark = projectile == null ? null : marks.remove(projectile);
        return mark == null || nowTicks >= mark.expiry() ? 0 : mark.value();
    }
}
