package engine.stores;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * One player's fixed-size (64) int-packed flight recorder of activation attempts (ADR-0045): the newest 64
 * {@code ActivationPipeline.evaluate} verdicts, decoded lazily at render time. Pure primitives (no VarHandle
 * — deliberate, for the JDK-8 legacy jar under JvmDowngrader), so it is ArchUnit-clean in the hot-path
 * {@code engine.stores} package.
 *
 * <p><b>Consistency model.</b> Writers (any region thread on Folia) claim a unique slot with
 * {@code head.getAndIncrement()}, so two concurrent writers to one ring — e.g. the ATTACK pass on the
 * victim's region and a same-tick DEFENSE pass on the player's own region — never lose or co-write a record.
 * Each slot is a per-slot seqlock: lane writes are plain, bracketed by an in-progress stamp before and a
 * release stamp ({@code AtomicLongArray.set}, volatile) after. A reader validates {@code stamp == seq+1}
 * before AND after copying the lanes and drops the row on a mismatch, so it can never render a torn mix of
 * two attempts and never blocks a writer. A reader may MISS rows still in flight at the head, or rows
 * overwritten while copying (only if &ge;64 newer attempts land mid-copy); it never renders garbage. Reads
 * take no lock and add zero cost to the record path.
 */
public final class WhyRing {

    public static final int SIZE = 64;               // power of two
    private static final int MASK = SIZE - 1;

    private final AtomicLong head = new AtomicLong();               // next sequence to claim (monotonic)
    private final AtomicLongArray stamp = new AtomicLongArray(SIZE); // seqlock stamp per slot (see class doc)
    private final long[] tick = new long[SIZE];      // Activation.nowTicks at the attempt
    private final int[] defId = new int[SIZE];       // Ability.defId (SourceMap key)
    private final int[] meta = new int[SIZE];        // packed verdict|trigger|gen (packMeta)
    private final int[] pA = new int[SIZE];          // gate payload A (per-verdict, ADR-0045 §6)
    private final int[] pB = new int[SIZE];          // gate payload B

    /**
     * Record one attempt. Claim a slot &rarr; in-progress stamp &rarr; plain lane writes &rarr; release stamp
     * (the per-slot seqlock the reader uses to detect a torn row instead of trusting it).
     */
    public void record(long nowTicks, int metaWord, int defIdV, int a, int b) {
        long seq = head.getAndIncrement();
        int i = (int) (seq & MASK);
        stamp.set(i, -(seq + 1));       // in-progress marker (never a valid published stamp)
        tick[i] = nowTicks;
        defId[i] = defIdV;
        meta[i] = metaWord;
        pA[i] = a;
        pB[i] = b;
        stamp.set(i, seq + 1);          // publish: the stamp for logical row `seq` is seq+1 (0 = never written)
    }

    /** One decoded attempt; torn rows are dropped during {@link #copy()}. */
    public record Attempt(long seq, long tick, int defId, int verdict, int triggerId, int gen, int pA, int pB) {
    }

    /**
     * Snapshot the ring newest-first. Benign-racy: validates each slot's stamp before AND after reading its
     * lanes; a mismatch (in-flight, or overwritten mid-copy) drops that row rather than rendering garbage.
     */
    public List<Attempt> copy() {
        long h = head.get();
        List<Attempt> out = new ArrayList<>((int) Math.min(h, SIZE));
        for (long seq = h - 1; seq >= 0 && seq > h - 1 - SIZE; seq--) {
            int i = (int) (seq & MASK);
            if (stamp.get(i) != seq + 1) {
                continue; // in-flight / already recycled
            }
            long t = tick[i];
            int d = defId[i];
            int m = meta[i];
            int a = pA[i];
            int b = pB[i];
            if (stamp.get(i) != seq + 1) {
                continue; // torn mid-read — drop, never render garbage
            }
            out.add(new Attempt(seq, t, d, verdict(m), triggerId(m), gen(m), a, b));
        }
        return out;
    }

    // ── meta packing: [gen:8 @11][triggerId:6 @5][verdict:5 @0] ──────────────────────────────────────────
    // verdict = GateOutcome ordinal (11 values, 5 bits of room); triggerId cap 32 (the trigger namespace caps
    // at 32, 6 bits); gen & 0xFF stamps the build so a cross-reload row resolves against the right tables.

    public static int packMeta(int verdict, int triggerId, int gen) {
        return (verdict & 0x1F) | ((triggerId & 0x3F) << 5) | ((gen & 0xFF) << 11);
    }

    public static int verdict(int meta) {
        return meta & 0x1F;
    }

    public static int triggerId(int meta) {
        return (meta >>> 5) & 0x3F;
    }

    public static int gen(int meta) {
        return (meta >>> 11) & 0xFF;
    }

    // ── scope packing (SUPPRESSED / ON_COOLDOWN pA): [flavor:1 @30][scopeKind:2 @28][id:28] ──────────────
    // flavor 0 = transient per-activation set, 1 = timed store; scopeKind = ScopeKinds enchant/group/type.

    public static int packScope(int flavor, int scopeKind, int id) {
        return ((flavor & 1) << 30) | ((scopeKind & 0x3) << 28) | (id & 0x0FFF_FFFF);
    }

    public static int scopeFlavor(int pA) {
        return (pA >>> 30) & 1;
    }

    public static int scopeKind(int pA) {
        return (pA >>> 28) & 0x3;
    }

    public static int scopeId(int pA) {
        return pA & 0x0FFF_FFFF;
    }

    /** Test seam: force {@code slot} to {@code value} so {@link #copy()}'s seqlock drop can be exercised
     *  deterministically (the real torn window is a sub-microsecond race). Tests only. */
    void stampForTest(int slot, long value) {
        stamp.set(slot, value);
    }
}
