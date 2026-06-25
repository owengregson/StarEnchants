package item.worn;

import compile.model.Snapshot;
import java.util.UUID;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import org.bukkit.entity.LivingEntity;

/**
 * Per-player resolved equipment state (§5.4, §5.5): a concurrent UUID&rarr;{@link WornState} store.
 * {@link #refresh} re-resolves on the entity's OWN thread and stores the immutable result; the hot path
 * only {@link #get reads} it — the safe cross-region victim read on Folia (attacker reads the stored
 * {@code WornState}, never the victim's live equipment, §3.4). Cleared per player on quit, wholesale on
 * reload. The resolver is injected, so this stays a pure, server-free, concurrency-safe map.
 */
public final class WornStateStore {

    private final BiFunction<LivingEntity, Snapshot, WornState> resolver;
    private final ConcurrentHashMap<UUID, WornState> byPlayer = new ConcurrentHashMap<>();

    public WornStateStore(BiFunction<LivingEntity, Snapshot, WornState> resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    /** Resolve and store {@code entity}'s worn state. Call on the entity's own thread, never cross-region. */
    public WornState refresh(LivingEntity entity, Snapshot snapshot) {
        WornState worn = resolver.apply(entity, snapshot);
        byPlayer.put(entity.getUniqueId(), worn);
        return worn;
    }

    /** Stored worn state for {@code id}, or {@code null} if none yet. Safe from any region thread (immutable). */
    public WornState get(UUID id) {
        return byPlayer.get(id);
    }

    public void remove(UUID id) {
        byPlayer.remove(id);
    }

    /** Cleared wholesale on reload — the new snapshot's ids supersede the old. */
    public void clear() {
        byPlayer.clear();
    }
}
