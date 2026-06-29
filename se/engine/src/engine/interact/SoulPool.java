package engine.interact;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The per-PLAYER soul authority for cross-gem soul mode (§D): the in-memory total a player may spend while in
 * soul mode, so the gate-10 affordability check (which may run on a FOREIGN region thread — combat fires on the
 * victim's region while the gem-holder is the attacker) never has to read the holder's inventory. The actual
 * souls live on the gems' PDC; this pool is the spend ledger over their SUM.
 *
 * <p><strong>The invariant</strong> (re-established by {@link #resync} on the holder thread): {@code available ==
 * Σ(physical gem souls) − pending}, where {@code pending} is souls spent here but not yet drained from the
 * physical gems. {@link #trySpend} (any thread, atomic) lowers {@code available} and raises {@code pending};
 * {@link #takePending} hands the holder thread the souls to drain least-first; {@link #resync} corrects
 * {@code available} for external inventory changes (pickup/drop) WITHOUT manufacturing or destroying souls
 * (pending is the only thing that could otherwise be mistaken for a gem the player gained/lost).
 *
 * <p>Per-player reads/writes are serialised by a fixed stripe-lock array (constant memory; the same player always
 * maps to the same stripe), so each compound update is atomic.
 */
public final class SoulPool {

    private static final int DEFAULT_STRIPES = 64;

    private final Object[] stripes;
    private final int mask;
    private final Map<UUID, int[]> state = new ConcurrentHashMap<>(); // player → [available, pending]; absent = not in soul mode

    public SoulPool() {
        this(DEFAULT_STRIPES);
    }

    /** @param stripeCount number of lock stripes; rounded up to a power of two. */
    public SoulPool(int stripeCount) {
        int n = Integer.highestOneBit(Math.max(1, stripeCount - 1)) << 1;
        this.stripes = new Object[n];
        for (int i = 0; i < n; i++) {
            stripes[i] = new Object();
        }
        this.mask = n - 1;
    }

    /** Begin soul mode for {@code player} with {@code total} spendable souls (their gems' sum) and no pending. */
    public void enable(UUID player, int total) {
        synchronized (stripeFor(player)) {
            state.put(player, new int[] {Math.max(0, total), 0});
        }
    }

    /** End soul mode for {@code player} (drop the pool). */
    public void disable(UUID player) {
        synchronized (stripeFor(player)) {
            state.remove(player);
        }
    }

    public boolean isActive(UUID player) {
        return state.containsKey(player);
    }

    /**
     * Atomically spend {@code cost} souls from {@code player}'s pool: lowers {@code available}, raises
     * {@code pending}. {@code true} iff in soul mode AND the pool can afford the full cost (all-or-nothing — no
     * partial spend). A non-positive cost is always affordable and spends nothing.
     */
    public boolean trySpend(UUID player, int cost) {
        if (cost <= 0) {
            return true;
        }
        synchronized (stripeFor(player)) {
            int[] s = state.get(player);
            if (s == null || s[0] < cost) {
                return false;
            }
            s[0] -= cost;
            s[1] += cost;
            return true;
        }
    }

    /** Hand the holder thread the souls spent-but-not-yet-drained (resets pending to 0); 0 if not in soul mode. */
    public int takePending(UUID player) {
        synchronized (stripeFor(player)) {
            int[] s = state.get(player);
            if (s == null) {
                return 0;
            }
            int pending = s[1];
            s[1] = 0;
            return pending;
        }
    }

    /** Re-establish the invariant from the physical truth: {@code available = max(0, physicalTotal − pending)}. */
    public void resync(UUID player, int physicalTotal) {
        synchronized (stripeFor(player)) {
            int[] s = state.get(player);
            if (s != null) {
                s[0] = Math.max(0, physicalTotal - s[1]);
            }
        }
    }

    /** The current spendable total for {@code player} (in-memory; safe from any thread), or 0 if not in soul mode. */
    public int total(UUID player) {
        synchronized (stripeFor(player)) {
            int[] s = state.get(player);
            return s == null ? 0 : s[0];
        }
    }

    /** Drop every player's pool (call on disable). */
    public void clearAll() {
        state.clear();
    }

    private Object stripeFor(UUID player) {
        int h = player.hashCode();
        h ^= (h >>> 16);
        return stripes[h & mask];
    }
}
