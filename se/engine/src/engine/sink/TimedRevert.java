package engine.sink;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The per-boot registry of a player's outstanding timed-buff reverts (temporary FLY / INVINCIBLE /
 * MOVEMENT_SPEED and the max-health drain give-back). Each timed grant registers its revert closure here at
 * grant time and holds a {@code token}; the normal expiry timer claims-and-runs that token via
 * {@link #runOnce}, and {@link feature.combat.TimedRevertListener} drains every outstanding revert on quit via
 * {@link #revertAll}.
 *
 * <p>The timer covers the normal case; the quit drain covers the logout edge (F07/F08), and the claimed token
 * means a revert can never run twice or land on a disconnected player — a late Paper timer whose token was
 * already claimed by the quit drain simply no-ops instead of mutating a stale offline {@code Player}. Every
 * registered revert must be idempotent / exact-delta (the four callers all are), so run order is irrelevant.
 *
 * <p>Wired instance-per-boot through the {@link SinkEnv} like {@code tempBlocks}/{@code trails}, never a mutable
 * static. All access for a given player runs on that player's owning region thread (grant flush, entity timer,
 * quit), so a plain {@link ConcurrentHashMap} needs no further per-player synchronisation.
 */
public final class TimedRevert {

    private final Map<UUID, ConcurrentHashMap<Long, Runnable>> active = new ConcurrentHashMap<>();
    private final AtomicLong tokens = new AtomicLong();

    /** Register {@code revert} for {@code player} and return the token the expiry timer will claim. */
    public long begin(UUID player, Runnable revert) {
        long token = tokens.incrementAndGet();
        active.computeIfAbsent(player, k -> new ConcurrentHashMap<>()).put(token, revert);
        return token;
    }

    /** Claim {@code token} and run its revert iff still outstanding (the quit drain may already have run it). */
    public void runOnce(UUID player, long token) {
        ConcurrentHashMap<Long, Runnable> byToken = active.get(player);
        if (byToken == null) {
            return;
        }
        Runnable revert = byToken.remove(token);
        if (byToken.isEmpty()) {
            active.remove(player);
        }
        if (revert != null) {
            revert.run();
        }
    }

    /** Drain and run every outstanding revert for {@code player} (the logout edge); claimed tokens then no-op. */
    public void revertAll(UUID player) {
        Map<Long, Runnable> byToken = active.remove(player);
        if (byToken == null) {
            return;
        }
        for (Runnable revert : byToken.values()) {
            revert.run();
        }
    }

    /** Drop a player's outstanding reverts without running them (disable/reset). */
    public void clear(UUID player) {
        active.remove(player);
    }

    /** Whether no player has an outstanding revert (tests / disable assertions). */
    public boolean isEmpty() {
        return active.isEmpty();
    }
}
