package engine.stores;

import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Armed head trophies ({@code HEAD_TROPHY}): a victim marked here drops a skull of themselves on their NEXT death
 * from any cause, then the mark is spent. The templates travel with the mark because the enchant that armed it is
 * long gone by the time the death fires — the death runs on the victim, and no trigger walks an attacker's gear
 * there.
 *
 * <p>Deliberately UNEXPIRING and retained across a relog (the measured contract: the flag waits as long as it
 * takes), which is why it carries its own {@link #CAPACITY} bound instead of relying on a TTL sweep — an
 * unexpiring per-player map with no ceiling is a leak on a long-lived server. At the cap the oldest arm is
 * dropped: a trophy nobody has collected in thousands of kills is the one worth losing.
 */
public final class HeadTrophyStore implements RetainedStore {

    /** How many armed trophies are kept at once; the oldest is dropped beyond it. */
    static final int CAPACITY = 1024;

    /**
     * One armed trophy: the raw display-name and lore templates (brace tokens unresolved — they resolve against
     * the death, not the arm) and the tick it was armed on, which orders the capacity eviction.
     */
    public record Trophy(String name, String lore, long armedTick) {
    }

    private final Map<UUID, Trophy> armed = new ConcurrentHashMap<>();

    /** Arm a trophy on {@code victim}, replacing any earlier one (the latest killer's templates win). */
    public void arm(UUID victim, String name, String lore, long nowTicks) {
        if (victim == null) {
            return;
        }
        armed.put(victim, new Trophy(name == null ? "" : name, lore == null ? "" : lore, nowTicks));
        if (armed.size() > CAPACITY) {
            armed.entrySet().stream()
                    .min(Comparator.comparingLong(e -> e.getValue().armedTick()))
                    .ifPresent(oldest -> armed.remove(oldest.getKey(), oldest.getValue()));
        }
    }

    /** Take {@code victim}'s armed trophy, clearing it — {@code null} when none is armed. */
    public Trophy consume(UUID victim) {
        return armed.remove(victim);
    }

    @Override
    public void clear(UUID victim) {
        armed.remove(victim);
    }

    @Override
    public void evictElapsed(UUID victim, long nowTicks) {
        // A trophy has no expiry: it waits for the death that spends it, through any number of relogs.
    }

    @Override
    public void evictElapsed(long nowTicks) {
        // See evictElapsed(UUID, long) — the capacity bound in arm() is what keeps this store finite.
    }

    /** Forget every armed trophy (call on disable). */
    public void clearAll() {
        armed.clear();
    }
}
