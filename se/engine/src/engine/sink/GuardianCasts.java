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

    /** A summon's provenance: who owns it, and the interned {@code group:} of the ability that summoned it. */
    private record Owned(UUID owner, int sourceGroup) {
    }

    private static final Map<UUID, Owned> OWNER_BY_ENTITY = new ConcurrentHashMap<>();

    /** Bind a freshly-summoned guardian to its owner (a hit on it will fire the owner's GUARDIAN_HURT). */
    public static void bind(UUID entity, UUID owner) {
        bind(entity, owner, -1);
    }

    /**
     * {@link #bind(UUID, UUID)} recording the summoning ability's group, so the {@code PHASE_STRIKE} courier can
     * fire only that feature's {@code IMPACT} abilities (ADR-0074). {@code -1} = unscoped.
     */
    public static void bind(UUID entity, UUID owner, int sourceGroup) {
        if (entity != null && owner != null) {
            OWNER_BY_ENTITY.put(entity, new Owned(owner, sourceGroup));
        }
    }

    /** The owner of {@code entity}, or {@code null} when it is not a tracked guardian. */
    public static UUID owner(UUID entity) {
        Owned owned = entity == null ? null : OWNER_BY_ENTITY.get(entity);
        return owned == null ? null : owned.owner();
    }

    /** The group that summoned {@code entity}, or {@code -1} when it is untracked or was summoned ungrouped. */
    public static int groupOf(UUID entity) {
        Owned owned = entity == null ? null : OWNER_BY_ENTITY.get(entity);
        return owned == null ? -1 : owned.sourceGroup();
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
