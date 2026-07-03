package engine.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import engine.sink.TempBlockLedger.Key;
import engine.sink.TempBlockLedger.Pending;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The layered temp-block decision core, hand-computed with no Bukkit through a fake {@link
 * TempBlockLedger.BlockOps} over {@code Integer} type ids. Pins the compounding contract the closure-based
 * revert violated: overlapping placements stack, a buried layer's expiry repaints nothing, and the LAST layer
 * to revert restores the TRUE original — not an intermediate temp block.
 */
class TempBlockLedgerTest {

    private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final Key K = new Key(WORLD, 0, 64, 0);

    // Test-owned opaque type ids (the core only tests equality + re-sets them).
    private static final int AIR = 0;
    private static final int STONE = 1;
    private static final int MAGMA = 2;
    private static final int NETHERRACK = 3;
    private static final int ICE = 4;
    private static final int GOLD = 5;

    /** A world of block type ids; counts each seam call so the tests can pin "captured once", "restored once". */
    private static final class FakeBlocks implements TempBlockLedger.BlockOps<Integer> {
        private final Map<Key, Integer> current = new HashMap<>();
        private int captures;
        private int sets;
        private int restores;

        private FakeBlocks seed(Key key, int typeId) {
            current.put(key, typeId);
            return this;
        }

        private int typeAt(Key key) {
            return current.getOrDefault(key, AIR);
        }

        @Override
        public int readTypeId(Key key) {
            return current.getOrDefault(key, AIR);
        }

        @Override
        public void setTypeId(Key key, int typeId) {
            sets++;
            current.put(key, typeId);
        }

        @Override
        public Integer captureOriginal(Key key) {
            captures++;
            return current.getOrDefault(key, AIR);
        }

        @Override
        public void restoreOriginal(Key key, Integer original) {
            restores++;
            current.put(key, original);
        }
    }

    // (a) The exact devil repro: a MAGMA floor (100t) over STONE, a NETHERRACK trail (40t) laid on top later.
    @Test
    void devilMagmaFloorThenNetherrackTrail_trailRevertRestoresStoneNotMagma() {
        FakeBlocks blocks = new FakeBlocks().seed(K, STONE);
        TempBlockLedger<Integer> ledger = new TempBlockLedger<>(blocks);

        Pending magma = ledger.place(K, MAGMA, 100, 0);       // Hell's Kitchen floor at t0
        assertEquals(MAGMA, blocks.typeAt(K));
        Pending nether = ledger.place(K, NETHERRACK, 40, 90);  // walking trail at t0+90
        assertEquals(NETHERRACK, blocks.typeAt(K));
        assertEquals(1, blocks.captures, "the trail must NOT re-capture the magma as its original");

        // magma's revert at t100 finds it buried under the trail → drop the magma layer, leave netherrack
        ledger.revert(K, magma.layerId(), magma.seq(), 100);
        assertEquals(NETHERRACK, blocks.typeAt(K));

        // the netherrack's own revert at t130 restores STONE — the closure-based code ended at MAGMA forever
        ledger.revert(K, nether.layerId(), nether.seq(), 130);
        assertEquals(STONE, blocks.typeAt(K));
        assertEquals(1, blocks.restores);
    }

    // (b) Reverse order: the shorter trail on top expires first, repaints the still-live floor, then the floor
    // reverts to the true original.
    @Test
    void trailOnTopExpiresFirst_repaintsFloor_thenFloorRestoresOriginal() {
        FakeBlocks blocks = new FakeBlocks().seed(K, STONE);
        TempBlockLedger<Integer> ledger = new TempBlockLedger<>(blocks);

        Pending magma = ledger.place(K, MAGMA, 100, 0);        // floor, 100t
        Pending nether = ledger.place(K, NETHERRACK, 40, 10);  // trail on top, expires t50

        ledger.revert(K, nether.layerId(), nether.seq(), 50);
        assertEquals(MAGMA, blocks.typeAt(K), "popping the trail repaints the still-live floor");

        ledger.revert(K, magma.layerId(), magma.seq(), 100);
        assertEquals(STONE, blocks.typeAt(K));
    }

    // (c) Same material refreshed repeatedly coalesces to one layer: one capture, one set, stale-seq reverts
    // no-op, and exactly one restore at the final (extended) deadline.
    @Test
    void sameMaterialRefreshCoalesces_staleRevertsNoOp_singleRestoreAtFinalDeadline() {
        FakeBlocks blocks = new FakeBlocks().seed(K, STONE);
        TempBlockLedger<Integer> ledger = new TempBlockLedger<>(blocks);

        Pending p0 = ledger.place(K, ICE, 20, 0);
        Pending p5 = ledger.place(K, ICE, 20, 5);
        Pending p10 = ledger.place(K, ICE, 20, 10);
        Pending p15 = ledger.place(K, ICE, 20, 15);
        assertEquals(1, blocks.captures, "refreshes never re-capture");
        assertEquals(1, blocks.sets, "refreshes never re-place the block");
        assertEquals(ICE, blocks.typeAt(K));

        // every refresh bumped the layer's seq, so each earlier scheduled revert is stale
        ledger.revert(K, p0.layerId(), p0.seq(), 20);
        ledger.revert(K, p5.layerId(), p5.seq(), 25);
        ledger.revert(K, p10.layerId(), p10.seq(), 30);
        assertEquals(ICE, blocks.typeAt(K), "a stale-seq revert must not restore");
        assertEquals(0, blocks.restores);

        // only the final refresh's revert, at the extended deadline, restores — and exactly once
        ledger.revert(K, p15.layerId(), p15.seq(), 15 + p15.delayTicks());
        assertEquals(STONE, blocks.typeAt(K));
        assertEquals(1, blocks.restores);
    }

    // (d) The world changing the tile (a player mines it) drops the whole entry and restores NOTHING, so a
    // pending revert can never clobber the player's change.
    @Test
    void externalChangeDropsEntryWithoutRestoring() {
        FakeBlocks blocks = new FakeBlocks().seed(K, STONE);
        TempBlockLedger<Integer> ledger = new TempBlockLedger<>(blocks);

        Pending magma = ledger.place(K, MAGMA, 100, 0);
        Pending nether = ledger.place(K, NETHERRACK, 40, 10);

        blocks.current.put(K, AIR); // player mines the tile out from under us

        ledger.revert(K, nether.layerId(), nether.seq(), 50);
        assertEquals(AIR, blocks.typeAt(K), "an external change is never clobbered");
        assertEquals(0, blocks.restores);

        // the entry was dropped, so the buried magma layer's later revert is a no-op
        ledger.revert(K, magma.layerId(), magma.seq(), 100);
        assertEquals(AIR, blocks.typeAt(K));
        assertEquals(0, blocks.restores);
    }

    // (e) A three-deep stack whose layers expire out of stack order: the buried middle repaints nothing, the
    // top exposes the still-live bottom, and the bottom restores the original.
    @Test
    void threeDeepCompound_shuffledExpiry_eachLayerHandledCorrectly() {
        FakeBlocks blocks = new FakeBlocks().seed(K, STONE);
        TempBlockLedger<Integer> ledger = new TempBlockLedger<>(blocks);

        Pending a = ledger.place(K, GOLD, 200, 0);        // bottom, longest (deadline 200)
        Pending b = ledger.place(K, MAGMA, 55, 5);        // middle, expires t60
        Pending c = ledger.place(K, NETHERRACK, 110, 10); // top,    expires t120
        assertEquals(NETHERRACK, blocks.typeAt(K));
        assertEquals(1, blocks.captures);

        ledger.revert(K, b.layerId(), b.seq(), 60);
        assertEquals(NETHERRACK, blocks.typeAt(K), "a buried layer expiring repaints nothing");

        ledger.revert(K, c.layerId(), c.seq(), 120);
        assertEquals(GOLD, blocks.typeAt(K), "popping the top exposes the still-live bottom");

        ledger.revert(K, a.layerId(), a.seq(), 200);
        assertEquals(STONE, blocks.typeAt(K));
    }

    // (e′) Coincident deadlines: the top's revert also sweeps the now-exposed, already-expired middle in one
    // pass (the buried-expired-middle branch), landing straight on the live bottom.
    @Test
    void coincidentDeadlines_topRevertSweepsExposedExpiredMiddle() {
        FakeBlocks blocks = new FakeBlocks().seed(K, STONE);
        TempBlockLedger<Integer> ledger = new TempBlockLedger<>(blocks);

        Pending a = ledger.place(K, GOLD, 300, 0);        // bottom, deadline 300
        Pending b = ledger.place(K, MAGMA, 100, 0);       // middle, deadline 100
        Pending c = ledger.place(K, NETHERRACK, 100, 0);  // top,    deadline 100 (same tick as the middle)

        ledger.revert(K, c.layerId(), c.seq(), 100);
        assertEquals(GOLD, blocks.typeAt(K), "the exposed expired middle is skipped to the live bottom");

        ledger.revert(K, b.layerId(), b.seq(), 100); // B was swept with C → gone → no-op
        assertEquals(GOLD, blocks.typeAt(K));

        ledger.revert(K, a.layerId(), a.seq(), 300);
        assertEquals(STONE, blocks.typeAt(K));
    }

    // (f) A WALKER tile and a TEMP_BLOCK point at the SAME coordinate share ONE entry + ONE captured original
    // (the whole point of a single shared ledger) — the block layers over the walker, never clobbers it.
    @Test
    void platformAndBlockAtOneTileShareOneEntryAndOriginal() {
        FakeBlocks blocks = new FakeBlocks().seed(K, STONE);
        TempBlockLedger<Integer> ledger = new TempBlockLedger<>(blocks);

        Pending walker = ledger.place(K, ICE, 40, 0);   // WALKER tile
        Pending block = ledger.place(K, MAGMA, 20, 5);  // TEMP_BLOCK point at the same coordinate
        assertEquals(1, blocks.captures, "two sources share ONE captured original (stone), not two");
        assertEquals(MAGMA, blocks.typeAt(K));

        ledger.revert(K, block.layerId(), block.seq(), 25);
        assertEquals(ICE, blocks.typeAt(K), "the block layer pops → the walker ice shows through");

        ledger.revert(K, walker.layerId(), walker.seq(), 40);
        assertEquals(STONE, blocks.typeAt(K));
    }

    // (g) The canReplace decision, single-sourced here, consults the live block's air/liquid/solid predicates.
    @Test
    void replaceableConsultsTheLiveBlockPerMode() {
        assertTrue(TempBlockLedger.replaceable(0, true, false, false), "air-only replaces air");
        assertFalse(TempBlockLedger.replaceable(0, false, true, true), "air-only skips liquid/solid");

        assertTrue(TempBlockLedger.replaceable(1, true, false, false), "air/liquid replaces air");
        assertTrue(TempBlockLedger.replaceable(1, false, true, false), "air/liquid replaces liquid");
        assertFalse(TempBlockLedger.replaceable(1, false, false, true), "air/liquid skips a solid");

        assertTrue(TempBlockLedger.replaceable(3, false, false, true), "solid-only replaces a solid");
        assertFalse(TempBlockLedger.replaceable(3, true, false, false), "solid-only skips air (no scaffolding)");

        assertTrue(TempBlockLedger.replaceable(2, false, false, false), "mode 2 replaces anything");
    }
}
