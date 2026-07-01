package engine.stores;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

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
    /**
     * Per-player suppression-immunity CHANCE in {@code [1,100]} (dragon's Dovahkiin, ADR-0034): each
     * {@link #suppress} rolls it, so {@code 100} is absolute immunity and a lower value ignores that fraction of
     * suppressions. Absent = not immune. A {@code SUPPRESS_IMMUNE} with no chance stores {@code 100}.
     */
    private final Map<UUID, Integer> immuneChance = new ConcurrentHashMap<>();
    private volatile SuppressListener onSuppress = (player, durationTicks) -> { };

    /**
     * Set {@code player}'s suppression-immunity CHANCE (percent), or lift it with {@code chance <= 0}
     * ({@code SUPPRESS_IMMUNE}). A full ({@code >= 100}) immunity also CLEARS any suppression already on the
     * player, so equipping it frees them at once; a partial chance only gates FUTURE suppressions.
     */
    public void setImmune(UUID player, int chance) {
        if (chance <= 0) {
            immuneChance.remove(player);
            return;
        }
        int clamped = Math.min(100, chance);
        immuneChance.put(player, clamped);
        if (clamped >= 100) {
            expiryByPlayer.remove(player); // absolute immunity drops any DISABLE that landed before it armed
        }
    }

    /** Whether {@code player} is currently ABSOLUTELY immune to suppression (a partial chance is not). */
    public boolean isImmune(UUID player) {
        return immuneChance.getOrDefault(player, 0) >= 100;
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
        if (durationTicks <= 0) {
            return;
        }
        int immunity = immuneChance.getOrDefault(player, 0);
        // Roll the per-player immunity: >=100 is absolute (Dovahkiin), a lower chance ignores that fraction of
        // suppressions (crystals/chaos "4% chance to ignore Silence", ADR-0034). ThreadLocalRandom — no RNG is
        // threaded to this store, and it runs on the firing region thread.
        if (immunity >= 100 || (immunity > 0 && ThreadLocalRandom.current().nextInt(100) < immunity)) {
            return;
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
        immuneChance.remove(player);
    }

    /** Forget every suppression (and all immunity) for every player (call on disable). */
    public void clearAll() {
        expiryByPlayer.clear();
        immuneChance.clear();
    }
}
