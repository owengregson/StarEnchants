package engine.stores;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player outgoing-damage debuffs for the Weaken enchants (ADR-0049: Destruction's non-stacking outgoing nerf):
 * while a player is debuffed, {@code CombatDispatch} subtracts {@code percent}% from their attack fold. Written by
 * the {@code WEAKEN} and {@code OUTGOING_DEBUFF} effects (who = victim), consulted on that victim's later attack
 * side. {@code OUTGOING_DEBUFF} adds the two axes WEAKEN has no room for: a damage-cause filter (a projectile-only
 * nerf leaves melee alone) and a per-hit feedback line.
 *
 * <p>NON-STACKING by contract: a re-debuff keeps the STRONGER window and the LATER expiry, never sums — several
 * attackers debuffing one victim cannot compound into a runaway nerf. The stronger percent carries its OWN filter
 * and feedback rather than merging them field-by-field, so a live window is always one authored debuff and never a
 * chimera of two. Retained across a relog like the other opponent-landed combat windows.
 */
public final class OutgoingDebuffStore implements RetainedStore {

    /** The melee half of the cause filter — a hit the debuffed player landed in person. */
    public static final int CAUSE_MELEE = 1;

    /** The projectile half — any projectile they loosed, not just an arrow. */
    public static final int CAUSE_PROJECTILE = 2;

    /** Both halves: the unfiltered debuff {@code WEAKEN} has always applied. */
    public static final int CAUSE_ALL = CAUSE_MELEE | CAUSE_PROJECTILE;

    /**
     * One debuff window: the outgoing-damage percentage removed, which cause halves it prices, the line sent to
     * the debuffed player on each hit it modifies ({@code ""} = silent), and the expiry tick.
     */
    public record Debuff(double percent, int causeMask, String feedback, long expiry) {

        /** Whether this window prices a hit of {@code causeBit} ({@link #CAUSE_MELEE} or {@link #CAUSE_PROJECTILE}). */
        public boolean covers(int causeBit) {
            return (causeMask & causeBit) != 0;
        }
    }

    private final Map<UUID, Debuff> debuffs = new ConcurrentHashMap<>();

    /**
     * Debuff {@code player}'s outgoing damage by {@code percent}% for {@code durationTicks}, priced only on hits
     * matching {@code causeMask}, with {@code feedback} sent on each hit it modifies. A non-positive percent,
     * duration, or mask is a no-op; a re-debuff keeps the stronger window and the later expiry (never sums).
     */
    public void debuff(UUID player, double percent, int causeMask, String feedback, long nowTicks,
                       int durationTicks) {
        if (player == null || percent <= 0 || durationTicks <= 0 || causeMask == 0) {
            return;
        }
        Debuff fresh = new Debuff(percent, causeMask, feedback == null ? "" : feedback, nowTicks + durationTicks);
        debuffs.merge(player, fresh, OutgoingDebuffStore::stronger);
    }

    /** {@link #debuff} unfiltered and silent — the shape {@code WEAKEN} has always written. */
    public void weaken(UUID player, double percent, long nowTicks, int durationTicks) {
        debuff(player, percent, CAUSE_ALL, "", nowTicks, durationTicks);
    }

    /** The harsher of two windows, carrying the later expiry — the non-stacking merge. */
    private static Debuff stronger(Debuff a, Debuff b) {
        Debuff harsher = b.percent() > a.percent() ? b : a;
        long expiry = Math.max(a.expiry(), b.expiry());
        return expiry == harsher.expiry() ? harsher
                : new Debuff(harsher.percent(), harsher.causeMask(), harsher.feedback(), expiry);
    }

    /** The active outgoing-damage debuff for {@code player} at {@code nowTicks}, or {@code null} if none/elapsed. */
    public Debuff active(UUID player, long nowTicks) {
        Debuff debuff = debuffs.get(player);
        if (debuff == null) {
            return null;
        }
        if (nowTicks >= debuff.expiry()) {
            debuffs.remove(player, debuff);
            return null;
        }
        return debuff;
    }

    @Override
    public void clear(UUID player) {
        debuffs.remove(player);
    }

    @Override
    public void evictElapsed(UUID player, long nowTicks) {
        debuffs.computeIfPresent(player, (id, debuff) -> nowTicks >= debuff.expiry() ? null : debuff);
    }

    @Override
    public void evictElapsed(long nowTicks) {
        debuffs.values().removeIf(debuff -> nowTicks >= debuff.expiry());
    }

    /** Forget every outgoing debuff (call on disable). */
    public void clearAll() {
        debuffs.clear();
    }
}
