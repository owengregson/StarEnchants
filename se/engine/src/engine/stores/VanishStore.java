package engine.stores;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * {@code VANISH} windows (Feign Death): who is hidden from every player right now, how many of their own
 * landed hits the window still absorbs, and the restore that ends it.
 *
 * <p>{@code VIEWER_HIDE} is the near miss and it is a different thing: its restore is scheduled at arm time
 * with nothing to cancel it, so nothing can end it early. The generation {@code seq} here is exactly what a
 * break-on-hit window needs — the expiry timer presents the seq it armed with, so a window a landed hit (or a
 * re-proc) already replaced refuses the stale timer instead of un-hiding somebody mid-vanish.
 *
 * <p>The restore rides the window rather than the caller because FOUR paths end a vanish — the timer, the
 * exhausting hit, the quit sweep and a reader finding it lapsed — and a hide that outlives its subject is
 * per-connection state no relog clears. It must be idempotent; {@link #close} guarantees only one path runs it.
 *
 * <p>Self-armed, self-benefiting combat state — swept VOLATILE on quit. Hot-path package rules apply
 * (concurrent map only).
 */
public final class VanishStore implements PlayerScoped {

    /** One live vanish: its generation, when it lapses, the landed hits it still absorbs, and its restore. */
    public record Window(long seq, long expiryTick, int hitsLeft, Runnable restore) {
    }

    private final Map<UUID, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    /**
     * Open a window on {@code subject}, REPLACING any live one — a re-proc refreshes the window rather than
     * extending it, and never inherits the hits the old one had already spent. Returns the generation the
     * expiry timer must present to {@link #close}. {@code freeHits <= 0} means only the timer can end it.
     */
    public long open(UUID subject, long nowTicks, int durationTicks, int freeHits, Runnable restore) {
        long seq = sequence.incrementAndGet();
        Window replaced = windows.put(subject, new Window(seq, nowTicks + durationTicks,
                Math.max(0, freeHits), restore));
        if (replaced != null) {
            replaced.restore().run(); // the old window's hide is subsumed; its var write must not outlive it
        }
        return seq;
    }

    /**
     * {@code subject}'s live window at {@code nowTicks}, or {@code null}. A lapsed window found here is closed
     * AND restored: a timer that never fired (an unloaded region, a dropped task) would otherwise strand every
     * other client hiding a body that is standing right in front of them.
     */
    public Window active(UUID subject, long nowTicks) {
        Window live = windows.get(subject);
        if (live == null) {
            return null;
        }
        if (nowTicks >= live.expiryTick()) {
            if (windows.remove(subject, live)) {
                live.restore().run();
            }
            return null;
        }
        return live;
    }

    /** Whether {@code subject} is vanished right now — the join re-sync's only question. */
    public boolean vanished(UUID subject, long nowTicks) {
        return active(subject, nowTicks) != null;
    }

    /**
     * Spend one of {@code subject}'s absorbed hits. Returns the window this hit EXHAUSTED — the caller runs its
     * restore — or {@code null} while the window survives (including for a subject holding none at all).
     */
    public Window spendHit(UUID subject, long nowTicks) {
        Window live = active(subject, nowTicks);
        if (live == null || live.hitsLeft() <= 0) {
            return null;
        }
        int left = live.hitsLeft() - 1;
        if (left > 0) {
            windows.replace(subject, live, new Window(live.seq(), live.expiryTick(), left, live.restore()));
            return null;
        }
        return windows.remove(subject, live) ? live : null;
    }

    /** Close {@code subject}'s window iff {@code seq} still owns it; returns it (the caller runs the restore). */
    public Window close(UUID subject, long seq) {
        Window live = windows.get(subject);
        if (live == null || live.seq() != seq) {
            return null; // a landed hit or a re-proc already replaced it — the stale timer is a no-op
        }
        return windows.remove(subject, live) ? live : null;
    }

    /** A quit mid-vanish RESTORES rather than merely forgetting: a hidden set is per-connection, not per-body. */
    @Override
    public void clear(UUID player) {
        Window gone = windows.remove(player);
        if (gone != null) {
            gone.restore().run();
        }
    }

    /** End every live vanish (call on disable). */
    public void clearAll() {
        for (UUID subject : List.copyOf(windows.keySet())) {
            clear(subject);
        }
    }
}
