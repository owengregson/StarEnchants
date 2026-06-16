package item.worn;

import compile.model.Snapshot;
import java.util.UUID;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import org.bukkit.entity.LivingEntity;

/**
 * The per-player resolved equipment state (docs/architecture.md §5.4, §5.5): a concurrent
 * UUID&rarr;{@link WornState} store. {@link #refresh} re-resolves an entity's worn state on an
 * equipment change — on the entity's OWN thread — and stores the immutable result; the combat hot
 * path then only {@link #get reads} that pre-built snapshot, which is the safe cross-region victim
 * read on Folia (the attacker's thread reads the victim's stored {@code WornState}, never the victim's
 * live equipment, §3.4). Cleared per player on quit and wholesale on reload.
 *
 * <p>The resolution function is injected (e.g. {@code wornResolver::resolve}) so this store is a pure,
 * server-free, concurrency-safe map with no dependency on the resolver's collaborators.
 */
public final class WornStateStore {

    private final BiFunction<LivingEntity, Snapshot, WornState> resolver;
    private final ConcurrentHashMap<UUID, WornState> byPlayer = new ConcurrentHashMap<>();

    public WornStateStore(BiFunction<LivingEntity, Snapshot, WornState> resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    /**
     * Resolve {@code entity}'s worn state against {@code snapshot} and store it; returns the result.
     * Call on the entity's own thread (from an equip-change handler), never cross-region.
     */
    public WornState refresh(LivingEntity entity, Snapshot snapshot) {
        WornState worn = resolver.apply(entity, snapshot);
        byPlayer.put(entity.getUniqueId(), worn);
        return worn;
    }

    /**
     * The stored worn state for {@code id}, or {@code null} if none has been resolved yet. Safe to read
     * from any region thread (the value is immutable) — this is the combat hot-path accessor.
     */
    public WornState get(UUID id) {
        return byPlayer.get(id);
    }

    /** Forget one player's worn state (on quit). */
    public void remove(UUID id) {
        byPlayer.remove(id);
    }

    /** Forget every player's worn state (on reload — the new snapshot's ids supersede the old). */
    public void clear() {
        byPlayer.clear();
    }
}
