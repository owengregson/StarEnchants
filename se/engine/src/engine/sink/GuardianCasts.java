package engine.sink;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Transient registry of every summoned guardian (by entity UUID) and its owner, so a hit on the guardian can fire
 * the owner's {@code GUARDIAN_HURT} abilities (ADR-0049 Blood Link). Written by the {@code GUARD}/{@code
 * SPAWN_ENTITY} sink intents when an owner is present, read by {@code GuardianHurtListener}, and forgotten when the
 * summon's TTL removes it.
 *
 * <p>Era-agnostic on purpose (no entity PDC, which 1.8 lacks; no plugin-bound metadata) — the same static-map
 * pattern as {@link FallingBlockCasts} — and cleared on disable via a module Stop.
 */
public final class GuardianCasts {

    private GuardianCasts() {
    }

    private static final Map<UUID, UUID> OWNER_BY_ENTITY = new ConcurrentHashMap<>();

    /**
     * Bind a freshly-summoned guardian to its owner (a hit on it will fire the owner's GUARDIAN_HURT).
     *
     * <p>Deliberately owner-only. The IMPACT scoping group a {@code PHASE_STRIKE} courier carries (ADR-0074)
     * rides {@code SummonFlags} instead, because {@code payload-consume} forgets THIS registry before it
     * dispatches — a group stored here would be gone by the time the courier needed it.
     */
    public static void bind(UUID entity, UUID owner) {
        if (entity != null && owner != null) {
            OWNER_BY_ENTITY.put(entity, owner);
        }
    }

    /** The owner of {@code entity}, or {@code null} when it is not a tracked guardian. */
    public static UUID owner(UUID entity) {
        return entity == null ? null : OWNER_BY_ENTITY.get(entity);
    }

    /** Forget a guardian removed (its TTL elapsed / it died) — keeps the map from leaking. */
    public static void forget(UUID entity) {
        if (entity != null) {
            OWNER_BY_ENTITY.remove(entity);
        }
    }

    /** Drop all tracking (call on disable). */
    public static void clearAll() {
        OWNER_BY_ENTITY.clear();
    }
}
