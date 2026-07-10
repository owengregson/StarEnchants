package engine.stores;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player outgoing-damage debuffs for the Weaken enchants (ADR-0049: Destruction's non-stacking outgoing nerf):
 * while a player is debuffed, {@code CombatDispatch} subtracts {@code percent}% from their attack fold. Written by
 * the {@code WEAKEN} effect (who = victim), consulted on that victim's later attack side.
 *
 * <p>NON-STACKING by contract: a re-debuff keeps the STRONGER percent and the LATER expiry, never sums — several
 * attackers debuffing one victim cannot compound into a runaway nerf. Retained across a relog like the other
 * opponent-landed combat windows.
 */
public final class OutgoingDebuffStore implements RetainedStore {

    /** One debuff window: the outgoing-damage percentage removed + the expiry tick. */
    private record Debuff(double percent, long expiry) {
    }

    private final Map<UUID, Debuff> debuffs = new ConcurrentHashMap<>();

    /**
     * Debuff {@code player}'s outgoing damage by {@code percent}% for {@code durationTicks}. A non-positive percent
     * or duration is a no-op; a re-debuff maxes percent and expiry component-wise (never sums).
     */
    public void weaken(UUID player, double percent, long nowTicks, int durationTicks) {
        if (player == null || percent <= 0 || durationTicks <= 0) {
            return;
        }
        long expiry = nowTicks + durationTicks;
        debuffs.merge(player, new Debuff(percent, expiry),
                (a, b) -> new Debuff(Math.max(a.percent(), b.percent()), Math.max(a.expiry(), b.expiry())));
    }

    /** The active outgoing-damage debuff percent for {@code player} at {@code nowTicks}, or {@code 0} if none/elapsed. */
    public double active(UUID player, long nowTicks) {
        Debuff debuff = debuffs.get(player);
        if (debuff == null) {
            return 0.0;
        }
        if (nowTicks >= debuff.expiry()) {
            debuffs.remove(player, debuff);
            return 0.0;
        }
        return debuff.percent();
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
