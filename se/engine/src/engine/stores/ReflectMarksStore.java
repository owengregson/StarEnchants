package engine.stores;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-afflicted reflect marks for the Hex enchants (ADR-0049): while {@code afflicted} is marked, a portion of
 * THEIR outgoing damage is dealt back to them (the marker hit them, so their own next swings hurt). Keyed by the
 * afflicted UUID — the attacker in a later hit — so {@code CombatDispatch} reads {@link #active} against the
 * damager. Written by the {@code REFLECT} effect; consulted at the fold-commit site.
 *
 * <p>Non-summing and monotone: a re-mark keeps the STRONGER fraction and the LATER expiry independently, so a
 * weaker re-mark can still extend the window and a shorter strong re-mark cannot cut it. Retained across a relog
 * (an opponent-landed combat window, like a DISABLE_* window) — a quick disconnect cannot shed it.
 */
public final class ReflectMarksStore implements RetainedStore {

    /**
     * One reflect window: the outgoing-damage percentage reflected back, the flat per-hit ceiling on the health
     * returned ({@code 0} = uncapped), the per-hit line shown to the afflicted (empty = silent), and the expiry.
     */
    public record Mark(double fractionPercent, double cap, String feedback, long expiry) {
    }

    private final Map<UUID, Mark> marks = new ConcurrentHashMap<>();

    /**
     * Mark {@code afflicted} so {@code fractionPercent}% of their outgoing damage reflects back for
     * {@code durationTicks}, bounded by {@code cap} health per hit and announced by {@code feedback}. A
     * non-positive fraction or duration is a no-op; a re-mark maxes fraction and expiry component-wise.
     */
    public void mark(UUID afflicted, double fractionPercent, double cap, String feedback,
                     long nowTicks, int durationTicks) {
        if (afflicted == null || fractionPercent <= 0 || durationTicks <= 0) {
            return;
        }
        long expiry = nowTicks + durationTicks;
        marks.merge(afflicted, new Mark(fractionPercent, cap, feedback == null ? "" : feedback, expiry),
                ReflectMarksStore::stronger);
    }

    /**
     * The component-wise stronger of an existing ({@code a}) and an incoming ({@code b}) window. Fraction and
     * expiry take the max; an UNCAPPED side wins the cap (0 is the loosest ceiling, not the tightest), else the
     * larger ceiling; and the newer feedback wins unless it is silent, so a bare re-mark cannot mute the line.
     */
    private static Mark stronger(Mark a, Mark b) {
        double cap = a.cap() <= 0 || b.cap() <= 0 ? 0 : Math.max(a.cap(), b.cap());
        return new Mark(Math.max(a.fractionPercent(), b.fractionPercent()), cap,
                b.feedback().isEmpty() ? a.feedback() : b.feedback(),
                Math.max(a.expiry(), b.expiry()));
    }

    /** The live reflect window for {@code afflicted} at {@code nowTicks}, or {@code null} if none/elapsed. */
    public Mark active(UUID afflicted, long nowTicks) {
        Mark mark = marks.get(afflicted);
        if (mark == null) {
            return null;
        }
        if (nowTicks >= mark.expiry()) {
            marks.remove(afflicted, mark);
            return null;
        }
        return mark;
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

    /** Forget every reflect mark (call on disable). */
    public void clearAll() {
        marks.clear();
    }
}
