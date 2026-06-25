package engine.interact;

import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The soul arbiter: the <em>single authority</em> for a gem's soul balance and the only
 * code that debits or credits souls (docs/architecture.md §6.3). The pipeline calls
 * {@link #tryConsume} at gate 10.
 *
 * <p><strong>Why in-memory.</strong> The durable balance lives on the gem's PDC, but on
 * Folia an {@code ItemStack}/PDC is a per-thread <em>copy</em>: two region threads each
 * see their own snapshot, so serialising writes to two snapshots wouldn't stop a
 * double-spend. This ledger keeps the authoritative count in memory keyed by the gem's
 * stable UUID — seeded from {@link Balance} on first touch, then the source of truth —
 * and writes each change through to the {@code Balance} so the durable copy stays in sync.
 * Because the count is shared, a second thread sees the first's debit even though their
 * PDC snapshots differ; that is what makes "one authority ⇒ no double-spend" hold.
 *
 * <p>Per-gem reads/writes are serialised by a <em>fixed</em> array of stripe locks
 * (constant memory, no per-gem monitor accumulation); the same gem always maps to the
 * same stripe, so its read-check-write is atomic.
 */
public final class SoulLedger {

    /**
     * A gem's durable soul balance — the caller's PDC proxy. Seeds the in-memory authority
     * once and is written through on every change. Backed by PDC in production, a plain
     * holder in tests.
     */
    public interface Balance {
        int souls();

        void setSouls(int souls);
    }

    private static final int DEFAULT_STRIPES = 64;

    private final Object[] stripes;
    private final int mask;
    private final Map<UUID, Integer> authoritative = new ConcurrentHashMap<>();

    public SoulLedger() {
        this(DEFAULT_STRIPES);
    }

    /** @param stripeCount number of lock stripes; rounded up to a power of two. */
    public SoulLedger(int stripeCount) {
        int n = Integer.highestOneBit(Math.max(1, stripeCount - 1)) << 1; // next power of two ≥ stripeCount
        this.stripes = new Object[n];
        for (int i = 0; i < n; i++) {
            stripes[i] = new Object();
        }
        this.mask = n - 1;
    }

    /**
     * Atomically spend {@code cost} souls from {@code gemId} if it can afford them, against
     * the in-memory authority (seeded from {@code balance} on first touch).
     *
     * @return {@code true} if paid (authority debited and written through to {@code balance});
     *     {@code false} if the gem had fewer than {@code cost} souls (nothing changed). A
     *     non-positive cost is always affordable and debits nothing.
     */
    public boolean tryConsume(UUID gemId, Balance balance, int cost) {
        if (cost <= 0) {
            return true;
        }
        synchronized (stripeFor(gemId)) {
            int current = currentLocked(gemId, balance);
            if (current < cost) {
                return false;
            }
            int next = current - cost;
            authoritative.put(gemId, next);
            balance.setSouls(next); // write through to the durable copy
            return true;
        }
    }

    /** Atomically credit {@code amount} souls to {@code gemId} (a non-positive amount is a no-op). */
    public void deposit(UUID gemId, Balance balance, int amount) {
        if (amount <= 0) {
            return;
        }
        synchronized (stripeFor(gemId)) {
            int next = currentLocked(gemId, balance) + amount;
            authoritative.put(gemId, next);
            balance.setSouls(next);
        }
    }

    /** The current authoritative balance for {@code gemId} (seeded from {@code balance} if unseen). */
    public int balance(UUID gemId, Balance balance) {
        synchronized (stripeFor(gemId)) {
            return currentLocked(gemId, balance);
        }
    }

    /**
     * The current authority for {@code gemId} WITHOUT seeding it — empty if untouched or
     * forgotten. Unlike {@link #balance}, never primes from a {@code Balance}, so a caller
     * flushing the durable copy can tell "no authoritative value" from "zero souls".
     */
    public OptionalInt peek(UUID gemId) {
        Integer value = authoritative.get(gemId);
        return value == null ? OptionalInt.empty() : OptionalInt.of(value);
    }

    /** Drop the in-memory authority for one gem (call when the gem is destroyed or goes inactive). */
    public void forget(UUID gemId) {
        authoritative.remove(gemId);
    }

    /** Drop every gem's in-memory authority (call on disable). */
    public void clearAll() {
        authoritative.clear();
    }

    /** The authoritative count, loading it from the durable balance on first touch. Caller holds the stripe. */
    private int currentLocked(UUID gemId, Balance balance) {
        Integer cached = authoritative.get(gemId);
        if (cached != null) {
            return cached;
        }
        int loaded = balance.souls();
        authoritative.put(gemId, loaded);
        return loaded;
    }

    private Object stripeFor(UUID gemId) {
        // Spread the bits so adjacent / low-entropy UUIDs distribute across stripes.
        int h = gemId.hashCode();
        h ^= (h >>> 16);
        return stripes[h & mask];
    }
}
