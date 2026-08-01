package engine.sink;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Transient registry of owned summons. Besides the owner used by GUARDIAN_HURT, each entry carries the
 * source-level summon family needed by cross-enchant interactions such as Cosmic Rot and Decay.
 */
public final class GuardianCasts {

    /** Cosmic metadata families; only the three named source tags are eligible for Rot and Decay purge. */
    public enum Kind {
        GUARDIAN(true),
        UNDEAD_RUSE(true),
        ANCESTRAL(true),
        OTHER(false);

        private final boolean rotAndDecayPurgeable;

        Kind(boolean rotAndDecayPurgeable) {
            this.rotAndDecayPurgeable = rotAndDecayPurgeable;
        }

        public boolean rotAndDecayPurgeable() {
            return rotAndDecayPurgeable;
        }
    }

    private record Binding(UUID owner, Kind kind, Runnable cleanup) {
    }

    private GuardianCasts() {
    }

    private static final Map<UUID, Binding> BY_ENTITY = new ConcurrentHashMap<>();

    /**
     * Bind a general owned spawn. Rebinding an existing summon (for example Conversion Bell) preserves its
     * source family and cleanup hook; a new untyped spawn is deliberately OTHER.
     */
    public static void bind(UUID entity, UUID owner) {
        if (entity == null || owner == null) {
            return;
        }
        BY_ENTITY.compute(entity, (ignored, current) -> current == null
                ? new Binding(owner, Kind.OTHER, null)
                : new Binding(owner, current.kind(), current.cleanup()));
    }

    /** Bind a summon to an explicit source family. */
    public static void bind(UUID entity, UUID owner, Kind kind) {
        bind(entity, owner, kind, null);
    }

    /** Bind a summon to an explicit source family and listener-local cleanup invoked when it is forgotten. */
    public static void bind(UUID entity, UUID owner, Kind kind, Runnable cleanup) {
        if (entity != null && owner != null) {
            BY_ENTITY.put(entity, new Binding(owner, kind == null ? Kind.OTHER : kind, cleanup));
        }
    }

    /** The owner of {@code entity}, or {@code null} when it is not a tracked summon. */
    public static UUID owner(UUID entity) {
        Binding binding = entity == null ? null : BY_ENTITY.get(entity);
        return binding == null ? null : binding.owner();
    }

    /** The source family of {@code entity}, defaulting to OTHER when it is not tracked. */
    public static Kind kind(UUID entity) {
        Binding binding = entity == null ? null : BY_ENTITY.get(entity);
        return binding == null ? Kind.OTHER : binding.kind();
    }

    /** Whether the entity carries one of Cosmic's three Rot and Decay purge metadata families. */
    public static boolean rotAndDecayPurgeable(UUID entity) {
        Binding binding = entity == null ? null : BY_ENTITY.get(entity);
        return binding != null && binding.kind().rotAndDecayPurgeable();
    }

    /** Forget a removed summon and release any listener-local state attached to it. */
    public static void forget(UUID entity) {
        if (entity == null) {
            return;
        }
        Binding removed = BY_ENTITY.remove(entity);
        runCleanup(removed);
    }

    /** Drop all tracking and release attached listener-local state on disable. */
    public static void clearAll() {
        ArrayList<Binding> removed = new ArrayList<>(BY_ENTITY.values());
        BY_ENTITY.clear();
        removed.forEach(GuardianCasts::runCleanup);
    }

    private static void runCleanup(Binding binding) {
        if (binding == null || binding.cleanup() == null) {
            return;
        }
        try {
            binding.cleanup().run();
        } catch (RuntimeException ignored) {
            // Cleanup is best-effort during entity removal/disable; the registry entry is already gone.
        }
    }
}
