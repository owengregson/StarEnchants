package engine.stores;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player "keep items on the next death" flag with a TTL (docs/architecture.md §5.4, §C combat-flags).
 * The {@code KEEP_ON_DEATH} effect arms it through the per-event {@link engine.sink.Sink} when its proc
 * fires (passing the gate sequence — chance/cooldown/conditions all apply); the death listener reads it on
 * {@code PlayerDeathEvent} and, if armed, keeps the player's items + levels.
 *
 * <p>The TTL is load-bearing: the engine has no {@code EffectKind.stop()}, so a worn passive has no
 * symmetric "on unequip" teardown. KEEP_ON_DEATH is therefore armed continuously by a {@code REPEATING}
 * ability (TTL &ge; the repeat period) while the item is worn, and the flag simply lapses a short time
 * after it stops being re-armed — i.e. after unequip. It is NOT consumed on a kept death (it is an
 * always-on enchant, not a one-shot scroll); re-arming keeps it live across deaths.
 *
 * <p>Concurrent and UUID-keyed for Folia (armed on the firing/repeating thread, read on the dying
 * player's region thread), TTL-evicting on read, re-arm extends (the later expiry wins). Cleared on quit
 * ({@link #clear}) and on disable ({@link #clearAll}). Time is an explicit caller-supplied tick, never
 * wall-clock — deterministic and unit-testable without a server.
 */
public final class KeepOnDeathStore {

    private final Map<UUID, Long> expiryByPlayer = new ConcurrentHashMap<>();

    /**
     * Arm "keep on death" for {@code player} for {@code ttlTicks}, expiring at {@code nowTicks + ttlTicks}.
     * A non-positive TTL is a no-op. Re-arming only ever EXTENDS the window (the later expiry wins), so a
     * shorter fresh arm never cuts an in-flight longer one short.
     */
    public void keep(UUID player, long nowTicks, int ttlTicks) {
        if (player == null || ttlTicks <= 0) {
            return;
        }
        expiryByPlayer.merge(player, nowTicks + ttlTicks, Math::max);
    }

    /**
     * @return {@code true} if {@code player} has an armed (non-elapsed) keep flag at {@code nowTicks}. An
     *     elapsed flag is evicted lazily and reported as not armed; the expiry tick itself counts as
     *     elapsed (the flag covers {@code [start, expiry)}).
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
