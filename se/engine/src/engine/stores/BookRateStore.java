package engine.stores;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player one-shot enchant-book success modifiers ({@code BOOK_RATE_MODIFIER}, the Blackscroll and
 * Enchanter pets): an additive percent armed against ONE book-economy site and spent by the next roll at that
 * site, whatever the roll returns.
 *
 * <p>The two sites are independent charges, so a generate charge and an apply charge coexist and neither can
 * spend the other. There is no expiry: the charge waits for its roll — which is what "the NEXT blackscroll
 * you use" means — and a re-arm is refused by the authored guard reading {@code %bookrate.generate%} /
 * {@code %bookrate.apply%}, not silently overwritten here.
 *
 * <p>RETAINED across a relog for the {@link HeadTrophyStore} reason: a charge armed under a 15-minute cooldown
 * that a reconnect silently ate would be a worse bug than the one being ported away from.
 */
public final class BookRateStore implements RetainedStore {

    /** A black scroll strips gear and mints a book — the modifier lands on the minted book's success rate. */
    public static final int GENERATE = 0;

    /** A book is applied to gear — the modifier lands on that apply roll. */
    public static final int APPLY = 1;

    /** How many sites exist; the values ARE the {@code site} enum's declaration ordinals. */
    public static final int COUNT = 2;

    /** The authored vocabulary, in ordinal order — the {@code BOOK_RATE_MODIFIER} spec's own enum values. */
    public static String[] names() {
        return new String[] {"generate", "apply"};
    }

    private final Map<UUID, int[]> charges = new ConcurrentHashMap<>();

    /** Arm {@code holder}'s charge at {@code site}. A non-positive percent arms nothing. */
    public void arm(UUID holder, int site, int percent) {
        if (holder == null || percent <= 0 || site < 0 || site >= COUNT) {
            return;
        }
        charges.computeIfAbsent(holder, k -> new int[COUNT])[site] = percent;
    }

    /** {@code holder}'s armed percent at {@code site} without spending it — the condition fact's read. */
    public int armed(UUID holder, int site) {
        if (charges.isEmpty() || holder == null || site < 0 || site >= COUNT) {
            return 0;
        }
        int[] slots = charges.get(holder);
        return slots == null ? 0 : slots[site];
    }

    /**
     * Take {@code holder}'s charge at {@code site}, clearing it — {@code 0} when none is armed. Called at the
     * ROLL, before its outcome is known: a charge is spent by the attempt, never refunded on a failure.
     */
    public int consume(UUID holder, int site) {
        if (charges.isEmpty() || holder == null || site < 0 || site >= COUNT) {
            return 0;
        }
        int[] slots = charges.get(holder);
        if (slots == null) {
            return 0;
        }
        int percent = slots[site];
        slots[site] = 0;
        if (slots[GENERATE] == 0 && slots[APPLY] == 0) {
            charges.remove(holder, slots);
        }
        return percent;
    }

    @Override
    public void clear(UUID player) {
        charges.remove(player);
    }

    @Override
    public void evictElapsed(UUID player, long nowTicks) {
        // A charge has no expiry: it waits for the roll that spends it, through any number of relogs.
    }

    @Override
    public void evictElapsed(long nowTicks) {
        // See evictElapsed(UUID, long) — two ints per armed player is what keeps this store finite.
    }

    /** Forget every player's charges (call on disable). */
    public void clearAll() {
        charges.clear();
    }
}
