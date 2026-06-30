package engine.stores;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player timed suppression: an interned id &rarr; expiry tick (docs/architecture.md §5.4). Home for
 * the {@code DISABLE_*}-with-duration effects (an enchant/group/type id silenced for a span of ticks). The
 * per-activation transient suppression set is a SEPARATE arbiter, not this; this store holds only
 * suppressions that outlast the activation that created them.
 */
public final class SuppressionStore {

    /** Notified whenever a player is freshly suppressed, so a maintained-buff driver can drop the affected
     *  effects immediately (instant DISABLE) and schedule their restore at the window's end. */
    @FunctionalInterface
    public interface SuppressListener {
        void onSuppress(UUID player, int durationTicks);
    }

    private final Map<UUID, Map<Long, Long>> expiryByPlayer = new ConcurrentHashMap<>();
    /** Players immune to ALL suppression (dragon's Dovahkiin): {@link #suppress} is a no-op for them. */
    private final Set<UUID> immune = ConcurrentHashMap.newKeySet();
    private volatile SuppressListener onSuppress = (player, durationTicks) -> { };

    /**
     * Make {@code player} immune to suppression, or lift it ({@code SUPPRESS_IMMUNE} / dragon's Dovahkiin).
     * Arming it also CLEARS any suppression already on the player, so equipping the set frees them at once.
     */
    public void setImmune(UUID player, boolean on) {
        if (on) {
            immune.add(player);
            expiryByPlayer.remove(player); // drop any DISABLE that landed before the immunity armed
        } else {
            immune.remove(player);
        }
    }

    /** Whether {@code player} is currently immune to suppression. */
    public boolean isImmune(UUID player) {
        return immune.contains(player);
    }

    /** Install the listener invoked after each {@link #suppress} (composition root); {@code null} clears it. */
    public void onSuppress(SuppressListener listener) {
        this.onSuppress = listener == null ? (player, durationTicks) -> { } : listener;
    }

    /**
     * Suppress packed scope key {@code id} for {@code durationTicks}, expiring at {@code nowTicks +
     * durationTicks}. The key is {@link CooldownStore#key(int, int)}-packed and shares the gate's
     * cooldown-scope namespace, so a {@code SUPPRESS} keys the same id the suppressed abilities lower their
     * scope to. Non-positive duration is a no-op; re-suppressing only EXTENDS (later expiry wins).
     */
    public void suppress(UUID player, long id, long nowTicks, int durationTicks) {
        if (durationTicks <= 0 || immune.contains(player)) {
            return; // a suppression-immune player (Dovahkiin) is never silenced
        }
        long expiry = nowTicks + durationTicks;
        expiryByPlayer.computeIfAbsent(player, k -> new ConcurrentHashMap<>())
                .merge(id, expiry, Math::max);
        onSuppress.onSuppress(player, durationTicks); // instant drop + scheduled restore of maintained buffs
    }

    /**
     * @return {@code true} if {@code id} has an active suppression for {@code player} at {@code nowTicks}.
     *     An elapsed one is evicted lazily; the window is half-open {@code [start, expiry)}.
     */
    public boolean isSuppressed(UUID player, long id, long nowTicks) {
        Map<Long, Long> ids = expiryByPlayer.get(player);
        if (ids == null) {
            return false;
        }
        Long expiry = ids.get(id);
        if (expiry == null) {
            return false;
        }
        if (nowTicks >= expiry) {
            ids.remove(id, expiry); // lazy eviction of an elapsed suppression
            return false;
        }
        return true;
    }

    /** Forget every suppression (and any immunity) for one player (call on quit). */
    public void clear(UUID player) {
        expiryByPlayer.remove(player);
        immune.remove(player);
    }

    /** Forget every suppression (and all immunity) for every player (call on disable). */
    public void clearAll() {
        expiryByPlayer.clear();
        immune.clear();
    }
}
