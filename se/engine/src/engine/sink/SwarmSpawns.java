package engine.sink;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Entity;
import platform.caps.Regions;

/**
 * Registry of live {@code SPAWN_SWARM} summons (ADR-0060) — the {@link PetSummons} pattern: a static
 * concurrent map keyed by entity UUID, deliberately era-agnostic (1.8 has no entity PDC). Entries drop
 * with each summon's TTL removal; {@link #removeAll} is the pets module's disable stop — best-effort on
 * Folia (a cross-region entity is skipped; its residual life is bounded by the short TTL).
 */
public final class SwarmSpawns {

    private static final Map<UUID, Entity> LIVE = new ConcurrentHashMap<>();

    private SwarmSpawns() {
    }

    public static void bind(Entity entity) {
        LIVE.put(entity.getUniqueId(), entity);
    }

    public static void forget(UUID entity) {
        LIVE.remove(entity);
    }

    /** Disable teardown: remove every live swarm summon (best-effort cross-region on Folia). */
    public static void removeAll() {
        for (Entity entity : LIVE.values()) {
            try {
                entity.remove();
            } catch (RuntimeException unreadable) {
                Regions.swallowed("SwarmSpawns.removeAll", unreadable);
            }
        }
        LIVE.clear();
    }
}
