package engine.stores;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player "keep items on the next death" flag with a TTL (docs/architecture.md §5.4, §C combat-flags).
 * The {@code KEEP_ON_DEATH} effect arms it via the {@link engine.sink.Sink}; the death listener reads it
 * on {@code PlayerDeathEvent} and, if armed, keeps the player's items + levels.
 *
 * <p>The TTL substitutes for an unequip teardown: the engine has no {@code EffectKind.stop()}, so the flag
 * is re-armed continuously by a {@code REPEATING} ability (TTL &ge; the repeat period) while worn, and
 * lapses shortly after re-arming stops — i.e. after unequip. NOT consumed on a kept death; re-arming keeps
 * it live across deaths.
 *
 * <p>Concurrent, UUID-keyed (Folia: armed on the firing thread, read on the dying player's region thread).
 * TTL-evicting on read; re-arm extends (later expiry wins). Time is an explicit caller-supplied tick,
 * never wall-clock — deterministic, server-free to test.
 */
public final class KeepOnDeathStore {

    private final Map<UUID, Long> expiryByPlayer = new ConcurrentHashMap<>();

    /**
     * Arm for {@code ttlTicks}, expiring at {@code nowTicks + ttlTicks}. Non-positive TTL is a no-op.
     * Re-arming only EXTENDS (later expiry wins), so a shorter fresh arm never cuts an in-flight one short.
     */
    public void keep(UUID player, long nowTicks, int ttlTicks) {
        if (player == null || ttlTicks <= 0) {
            return;
        }
        expiryByPlayer.merge(player, nowTicks + ttlTicks, Math::max);
    }

    /**
     * @return {@code true} if {@code player} has an armed (non-elapsed) keep flag at {@code nowTicks}.
     *     An elapsed flag is evicted lazily; the window is half-open {@code [start, expiry)}.
     */
    public boolean shouldKeep(UUID player, long nowTicks) {
        Long expiry = expiryByPlayer.get(player);
        if (expiry == null) {
            return false;
        }
        if (nowTicks >= expiry) {
            expiryByPlayer.remove(player, expiry); // lazy eviction
            return false;
        }
        return true;
    }

    /** Forget one player's keep flag (call on quit). */
    public void clear(UUID player) {
        expiryByPlayer.remove(player);
    }

    /** Forget every player's keep flag (call on disable). */
    public void clearAll() {
        expiryByPlayer.clear();
    }
}
