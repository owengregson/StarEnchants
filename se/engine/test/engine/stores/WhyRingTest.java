package engine.stores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Hand-computed round-trips for the packed flight-recorder ring (ADR-0045): packing, eviction, seqlock drop. */
class WhyRingTest {

    @Test
    void metaPacksVerdictTriggerAndGenIndependentlyAtBoundaries() {
        int meta = WhyRing.packMeta(10, 32, 255);
        assertEquals(10, WhyRing.verdict(meta));
        assertEquals(32, WhyRing.triggerId(meta));
        assertEquals(255, WhyRing.gen(meta));

        // Field widths: verdict 5 bits, trigger 6 bits, gen 8 bits — each saturates without bleeding into the next.
        int max = WhyRing.packMeta(31, 63, 255);
        assertEquals(31, WhyRing.verdict(max));
        assertEquals(63, WhyRing.triggerId(max));
        assertEquals(255, WhyRing.gen(max));

        int zero = WhyRing.packMeta(0, 0, 0);
        assertEquals(0, WhyRing.verdict(zero));
        assertEquals(0, WhyRing.triggerId(zero));
        assertEquals(0, WhyRing.gen(zero));

        // 8-bit gen truncates after 256 reloads (documented aliasing, ADR-0045 §5).
        assertEquals(0, WhyRing.gen(WhyRing.packMeta(0, 0, 256)));
    }

    @Test
    void scopePacksFlavorKindAndIdAtBoundaries() {
        int idMax = 0x0FFF_FFFF; // 28-bit id ceiling
        int packed = WhyRing.packScope(1, 2, idMax);
        assertEquals(1, WhyRing.scopeFlavor(packed));
        assertEquals(2, WhyRing.scopeKind(packed));
        assertEquals(idMax, WhyRing.scopeId(packed));

        int transient0 = WhyRing.packScope(0, 0, 7);
        assertEquals(0, WhyRing.scopeFlavor(transient0));
        assertEquals(0, WhyRing.scopeKind(transient0));
        assertEquals(7, WhyRing.scopeId(transient0));
    }

    @Test
    void singleAttemptRoundTripsEveryLane() {
        WhyRing ring = new WhyRing();
        ring.record(1000L, WhyRing.packMeta(5, 3, 7), 42, 100, 200);

        List<WhyRing.Attempt> out = ring.copy();
        assertEquals(1, out.size());
        WhyRing.Attempt a = out.get(0);
        assertEquals(0L, a.seq());
        assertEquals(1000L, a.tick());
        assertEquals(42, a.defId());
        assertEquals(5, a.verdict());
        assertEquals(3, a.triggerId());
        assertEquals(7, a.gen());
        assertEquals(100, a.pA());
        assertEquals(200, a.pB());
    }

    @Test
    void copyIsNewestFirst() {
        WhyRing ring = new WhyRing();
        for (int d = 10; d <= 12; d++) {
            ring.record(d, WhyRing.packMeta(0, 0, 0), d, 0, 0);
        }
        List<WhyRing.Attempt> out = ring.copy();
        assertEquals(List.of(12, 11, 10), out.stream().map(WhyRing.Attempt::defId).toList());
        assertEquals(List.of(2L, 1L, 0L), out.stream().map(WhyRing.Attempt::seq).toList());
    }

    @Test
    void sixtyFourFillThenTheOldestEvicts() {
        WhyRing ring = new WhyRing();
        for (int d = 0; d < WhyRing.SIZE; d++) {
            ring.record(d, WhyRing.packMeta(0, 0, 0), d, 0, 0);
        }
        assertEquals(WhyRing.SIZE, ring.copy().size());

        ring.record(64, WhyRing.packMeta(0, 0, 0), 64, 0, 0); // the 65th displaces seq 0
        List<WhyRing.Attempt> out = ring.copy();
        assertEquals(WhyRing.SIZE, out.size());
        assertEquals(64L, out.get(0).seq());                  // newest
        assertEquals(1L, out.get(out.size() - 1).seq());      // oldest surviving; seq 0 gone
        assertTrue(out.stream().noneMatch(a -> a.seq() == 0L));
    }

    @Test
    void copyDropsAnInFlightSlot() {
        WhyRing ring = new WhyRing();
        for (int d = 0; d < 5; d++) {
            ring.record(d, WhyRing.packMeta(0, 0, 0), d, 0, 0);
        }
        long seq = 2L;
        ring.stampForTest((int) (seq & (WhyRing.SIZE - 1)), -(seq + 1)); // re-arm the in-progress marker for seq 2

        List<WhyRing.Attempt> out = ring.copy();
        assertEquals(4, out.size());
        assertTrue(out.stream().noneMatch(a -> a.seq() == seq), "the in-flight row must be dropped");
        assertEquals(List.of(4L, 3L, 1L, 0L), out.stream().map(WhyRing.Attempt::seq).toList());
    }
}
