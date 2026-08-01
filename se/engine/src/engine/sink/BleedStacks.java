package engine.sink;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Shared 0..20 Cosmic Bleed stack registry. */
public final class BleedStacks {
    private BleedStacks() {
    }

    private static final ConcurrentHashMap<UUID, Integer> STACKS = new ConcurrentHashMap<>();

    public static int current(UUID entity) {
        return entity == null ? 0 : STACKS.getOrDefault(entity, 0);
    }

    public static int increment(UUID entity) {
        return STACKS.compute(entity, (id, old) -> Math.min(20, (old == null ? 0 : old) + 1));
    }

    public static void clear(UUID entity) {
        if (entity != null) {
            STACKS.remove(entity);
        }
    }

    public static void clearAll() {
        STACKS.clear();
    }
}
