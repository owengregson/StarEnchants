package engine.sink;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of live flagged summons (ADR-0052): {@code spawned entity UUID → its} {@link SummonFlags}. The
 * {@code GuardianCasts} pattern — a static concurrent map keyed by entity UUID, deliberately era-agnostic
 * (1.8 has no entity PDC) and deliberately separate from {@code GuardianCasts} (owner-tagging serves
 * GUARDIAN_HURT; double-purposing it would fire owner abilities on every summon hit). Read by the
 * summon-guard listener on the entity's own region thread; entries drop with the TTL removal, on
 * detonation, and wholesale on disable.
 */
public final class PetSummons {

    private static final Map<UUID, SummonFlags> FLAGS = new ConcurrentHashMap<>();

    private PetSummons() {
    }

    public static void bind(UUID entity, SummonFlags flags) {
        FLAGS.put(entity, flags);
    }

    /** The flags a tracked summon was spawned with, or {@code null} for an untracked entity. */
    public static SummonFlags flags(UUID entity) {
        return FLAGS.get(entity);
    }

    public static void forget(UUID entity) {
        FLAGS.remove(entity);
    }

    /** Disable/reload teardown (the module's stop). */
    public static void clearAll() {
        FLAGS.clear();
    }
}
