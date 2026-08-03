package engine.stores;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player marks that multiply the bearer's INCOMING damage from named vanilla damage-over-time causes
 * ({@code DOT_AMPLIFY_MARK}), read by the environmental damage path where wither/poison ticks land. Nothing else
 * reaches those ticks: {@code MARK} scales only the marker's own later hits, and the damage-mod kinds act on the
 * triggering fold.
 *
 * <p>REFRESH-ON-REAPPLY unconditionally — the later mark replaces the earlier one whole, weaker factor included.
 * That is the measured contract (a re-infection is a fresh infection), and it is the reason this store does not
 * share {@link OutgoingDebuffStore}'s keep-the-stronger merge. Retained across a relog: an opponent landed it.
 */
public final class DotAmplifyStore implements RetainedStore {

    /** The wither half of the cause filter. */
    public static final int CAUSE_WITHER = 1;

    /** The poison half. */
    public static final int CAUSE_POISON = 2;

    /** Every amplifiable damage-over-time cause. */
    public static final int CAUSE_DOT = CAUSE_WITHER | CAUSE_POISON;

    /** One mark: the multiplier, the cause halves it amplifies, and the expiry tick. */
    private record Amplify(double factor, int causeMask, long expiry) {
    }

    private final Map<UUID, Amplify> marks = new ConcurrentHashMap<>();

    /**
     * Mark {@code player} so incoming {@code causeMask} damage is multiplied by {@code factor} for
     * {@code durationTicks}. A factor of 1 or below, a non-positive duration, or an empty mask is a no-op;
     * anything else REPLACES a live mark outright.
     */
    public void amplify(UUID player, double factor, int causeMask, long nowTicks, int durationTicks) {
        if (player == null || factor <= 1.0 || durationTicks <= 0 || causeMask == 0) {
            return;
        }
        marks.put(player, new Amplify(factor, causeMask, nowTicks + durationTicks));
    }

    /**
     * The multiplier {@code player}'s incoming {@code causeBit} damage takes at {@code nowTicks}, or {@code 1.0}
     * when unmarked, elapsed, or the mark does not cover that cause.
     */
    public double factor(UUID player, long nowTicks, int causeBit) {
        Amplify mark = marks.get(player);
        if (mark == null) {
            return 1.0;
        }
        if (nowTicks >= mark.expiry()) {
            marks.remove(player, mark);
            return 1.0;
        }
        return (mark.causeMask() & causeBit) != 0 ? mark.factor() : 1.0;
    }

    @Override
    public void clear(UUID player) {
        marks.remove(player);
    }

    @Override
    public void evictElapsed(UUID player, long nowTicks) {
        marks.computeIfPresent(player, (id, mark) -> nowTicks >= mark.expiry() ? null : mark);
    }

    @Override
    public void evictElapsed(long nowTicks) {
        marks.values().removeIf(mark -> nowTicks >= mark.expiry());
    }

    /** Forget every mark (call on disable). */
    public void clearAll() {
        marks.clear();
    }
}
