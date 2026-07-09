package engine.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import engine.sink.TempBlockLedger.Key;
import engine.sink.TempBlockLedger.Pending;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    // (h) A scheduled revert that never fires (chunk unload / dropped region task) would strand the temp block
    // forever. A later place() past the newest deadline + grace self-heals: it restores the TRUE original, drops
    // the abandoned entry, then captures fresh — so the new layer sits over the original, never the leaked block.
    @Test
    void placeOverLongAbandonedEntry_selfHealsToOriginalThenPlacesFresh() {
        FakeBlocks blocks = new FakeBlocks().seed(K, STONE);
        TempBlockLedger<Integer> ledger = new TempBlockLedger<>(blocks);

        Pending magma = ledger.place(K, MAGMA, 100, 0); // floor at t0, deadline 100; its revert never fires
        assertEquals(MAGMA, blocks.typeAt(K));

        long late = 100 + TempBlockLedger.STALE_GRACE_TICKS + 1; // past deadline + grace → abandoned
        Pending ice = ledger.place(K, ICE, 40, late);
        assertEquals(ICE, blocks.typeAt(K));
        assertEquals(1, blocks.restores, "the abandoned magma was reverted to STONE before the fresh placement");
        assertEquals(2, blocks.captures, "the stale entry dropped, so ICE captured a FRESH original (the STONE)");

        // the abandoned magma's late revert is now a no-op (its entry is gone) — it can never clobber the ICE
        ledger.revert(K, magma.layerId(), magma.seq(), late);
        assertEquals(ICE, blocks.typeAt(K));
        assertEquals(1, blocks.restores);

        // ICE's own revert restores the TRUE original (stone), proving the self-heal re-based the capture
        ledger.revert(K, ice.layerId(), ice.seq(), late + ice.delayTicks());
        assertEquals(STONE, blocks.typeAt(K));
        assertEquals(2, blocks.restores);
    }

    // (h′) An entry only just past its newest deadline (inside the grace window) is a normal in-flight refresh,
    // NOT abandoned: place() layers over it and keeps the one captured original — the self-heal must not fire.
    @Test
    void placeWithinGraceWindow_doesNotSelfHeal_keepsSharedOriginal() {
        FakeBlocks blocks = new FakeBlocks().seed(K, STONE);
        TempBlockLedger<Integer> ledger = new TempBlockLedger<>(blocks);

        ledger.place(K, MAGMA, 100, 0);                                 // deadline 100
        ledger.place(K, NETHERRACK, 40, 100 + TempBlockLedger.STALE_GRACE_TICKS); // still within grace
        assertEquals(NETHERRACK, blocks.typeAt(K));
        assertEquals(1, blocks.captures, "within grace the entry is kept — no fresh capture, no self-heal restore");
        assertEquals(0, blocks.restores);
    }

    // ---- F25: reclaim (mine-as-early-revert) ----

    // Reclaiming a live single-layer tile restores the true original exactly once, drops the entry, and a later
    // scheduled revert for that layer is a no-op — so a mined temp block yields no drop and deletes no original.
    @Test
    void reclaimSingleLayer_restoresOriginalOnce_thenScheduledRevertNoOps() {
        FakeBlocks blocks = new FakeBlocks().seed(K, GOLD);
        TempBlockLedger<Integer> ledger = new TempBlockLedger<>(blocks);

        Pending magma = ledger.place(K, MAGMA, 100, 0);
        assertEquals(MAGMA, blocks.typeAt(K));

        assertTrue(ledger.reclaim(WORLD, 0, 64, 0));
        assertEquals(GOLD, blocks.typeAt(K), "reclaim restores the captured original");
        assertEquals(1, blocks.restores);
        assertTrue(ledger.isEmpty(), "the entry is gone after reclaim");

        ledger.revert(K, magma.layerId(), magma.seq(), 100); // the still-pending scheduled revert
        assertEquals(GOLD, blocks.typeAt(K));
        assertEquals(1, blocks.restores, "the scheduled revert no-ops on the now-absent entry");
    }

    // Reclaiming a STACKED tile (magma floor + netherrack trail) pops ALL layers and restores the TRUE original,
    // never the buried intermediate layer.
    @Test
    void reclaimStacked_restoresTrueOriginalNotIntermediateLayer() {
        FakeBlocks blocks = new FakeBlocks().seed(K, STONE);
        TempBlockLedger<Integer> ledger = new TempBlockLedger<>(blocks);

        ledger.place(K, MAGMA, 100, 0);
        ledger.place(K, NETHERRACK, 40, 10);
        assertEquals(NETHERRACK, blocks.typeAt(K));

        assertTrue(ledger.reclaim(WORLD, 0, 64, 0));
        assertEquals(STONE, blocks.typeAt(K), "the whole stack pops back to the true original");
        assertTrue(ledger.isEmpty());
    }

    // Reclaiming a tile the world changed out from under us returns false, restores nothing, and LEAVES the entry
    // for revert()'s drop branch — reclaim must never hijack a foreign block's break.
    @Test
    void reclaimAfterExternalChange_returnsFalse_leavesEntryForRevertDropBranch() {
        FakeBlocks blocks = new FakeBlocks().seed(K, STONE);
        TempBlockLedger<Integer> ledger = new TempBlockLedger<>(blocks);

        Pending magma = ledger.place(K, MAGMA, 100, 0);
        blocks.current.put(K, AIR); // the world diverged (some other plugin/piston changed it)

        assertFalse(ledger.reclaim(WORLD, 0, 64, 0));
        assertEquals(0, blocks.restores, "a diverged tile is never hijacked");
        assertFalse(ledger.isEmpty(), "the entry is retained for the scheduled revert");

        ledger.revert(K, magma.layerId(), magma.seq(), 100); // its own drop branch drops the entry, restores nothing
        assertEquals(AIR, blocks.typeAt(K));
        assertEquals(0, blocks.restores);
        assertTrue(ledger.isEmpty());
    }

    // Reclaiming an untracked tile is a false no-op.
    @Test
    void reclaimUntracked_isFalseNoOp() {
        FakeBlocks blocks = new FakeBlocks().seed(K, STONE);
        TempBlockLedger<Integer> ledger = new TempBlockLedger<>(blocks);
        assertFalse(ledger.reclaim(WORLD, 0, 64, 0));
        assertEquals(0, blocks.restores);
    }

    // guarded()/isEmpty() flip correctly across place / revert / reclaim.
    @Test
    void guardedAndIsEmpty_trackEntryLifecycle() {
        FakeBlocks blocks = new FakeBlocks().seed(K, STONE);
        TempBlockLedger<Integer> ledger = new TempBlockLedger<>(blocks);

        assertTrue(ledger.isEmpty());
        assertFalse(ledger.guarded(WORLD, 0, 64, 0));

        Pending p = ledger.place(K, ICE, 20, 0);
        assertFalse(ledger.isEmpty());
        assertTrue(ledger.guarded(WORLD, 0, 64, 0));
        assertFalse(ledger.guarded(WORLD, 1, 64, 0), "a neighbouring tile is not guarded");

        ledger.revert(K, p.layerId(), p.seq(), 20);
        assertTrue(ledger.isEmpty());
        assertFalse(ledger.guarded(WORLD, 0, 64, 0));
    }

    // ---- F29: chunk-load re-arm, self-heal sweep ----

    // rearmChunk emits every layer of every in-chunk key (delay clamped >= 1) and skips keys in other
    // chunks/worlds; feeding the emitted tuples back through revert after their deadlines restores the TRUE
    // original exactly once, and a duplicate delivery no-ops.
    @Test
    void rearmChunk_emitsEveryInChunkLayer_skipsOthers_revertIdempotent() {
        UUID otherWorld = UUID.fromString("00000000-0000-0000-0000-0000000000bb");
        Key farChunk = new Key(WORLD, 200, 64, 0); // chunkX 12 — different chunk
        Key otherWorldKey = new Key(otherWorld, 0, 64, 0);
        FakeBlocks blocks = new FakeBlocks().seed(K, STONE).seed(farChunk, GOLD).seed(otherWorldKey, ICE);
        TempBlockLedger<Integer> ledger = new TempBlockLedger<>(blocks);

        Pending magma = ledger.place(K, MAGMA, 100, 0);       // in chunk (0,0)
        Pending nether = ledger.place(K, NETHERRACK, 40, 10);  // second layer at K
        ledger.place(farChunk, MAGMA, 100, 0);                 // other chunk — must be skipped
        ledger.place(otherWorldKey, MAGMA, 100, 0);            // other world — must be skipped

        record Emit(int x, int y, int z, long id, long seq, long delay) { }
        List<Emit> emitted = new ArrayList<>();
        ledger.rearmChunk(WORLD, 0, 0, 130, (x, y, z, id, seq, delay) ->
                emitted.add(new Emit(x, y, z, id, seq, delay)));

        assertEquals(2, emitted.size(), "both layers of the sole in-chunk key are re-armed, nothing else");
        for (Emit e : emitted) {
            assertEquals(0, e.x());
            assertEquals(64, e.y());
            assertEquals(0, e.z());
            assertTrue(e.delay() >= 1, "a past-deadline layer clamps its delay to >= 1");
        }
        // Both deadlines (100, 50) are past now (130) → each re-armed with delay clamped to 1.
        long magmaSeq = emitted.stream().filter(e -> e.id() == magma.layerId()).findFirst().orElseThrow().seq();
        long netherSeq = emitted.stream().filter(e -> e.id() == nether.layerId()).findFirst().orElseThrow().seq();
        assertEquals(magma.seq(), magmaSeq);
        assertEquals(nether.seq(), netherSeq);

        // Deliver the re-armed reverts. Both layers are already past their deadlines (the stranded-reload case), so
        // popping the top netherrack sweeps the now-exposed expired magma in one pass straight to the TRUE original.
        ledger.revertAt(WORLD, 0, 64, 0, nether.layerId(), netherSeq, 131);
        assertEquals(STONE, blocks.typeAt(K), "a stranded stack heals straight to the true original on reload");
        // The magma delivery and a duplicate netherrack delivery are harmless no-ops (the entry is already gone).
        ledger.revertAt(WORLD, 0, 64, 0, magma.layerId(), magmaSeq, 131);
        ledger.revertAt(WORLD, 0, 64, 0, nether.layerId(), netherSeq, 131);
        assertEquals(STONE, blocks.typeAt(K));
        assertEquals(1, blocks.restores, "the true original is restored exactly once across re-arm + duplicates");
    }

    // rearmChunk emits a future-deadline layer with its real (positive) remaining delay, not the clamp.
    @Test
    void rearmChunk_futureDeadlineKeepsRealDelay() {
        FakeBlocks blocks = new FakeBlocks().seed(K, STONE);
        TempBlockLedger<Integer> ledger = new TempBlockLedger<>(blocks);
        ledger.place(K, MAGMA, 100, 0); // deadline 100

        long[] delay = {-1};
        ledger.rearmChunk(WORLD, 0, 0, 40, (x, y, z, id, seq, d) -> delay[0] = d);
        assertEquals(60, delay[0], "deadline 100 at now 40 re-arms with 60 ticks left");
    }

    // healIfExpired no-ops within the stale grace, restores-and-drops past it, and drops-without-restore when the
    // world diverged.
    @Test
    void healIfExpired_graceThenRestore_divergedDropsWithoutRestore() {
        FakeBlocks blocks = new FakeBlocks().seed(K, STONE);
        TempBlockLedger<Integer> ledger = new TempBlockLedger<>(blocks);
        ledger.place(K, MAGMA, 100, 0); // deadline 100

        ledger.healIfExpired(WORLD, 0, 64, 0, 100 + TempBlockLedger.STALE_GRACE_TICKS); // still within grace
        assertEquals(MAGMA, blocks.typeAt(K), "within grace this is a live entry — no heal");
        assertEquals(0, blocks.restores);
        assertFalse(ledger.isEmpty());

        ledger.healIfExpired(WORLD, 0, 64, 0, 100 + TempBlockLedger.STALE_GRACE_TICKS + 1); // past grace
        assertEquals(STONE, blocks.typeAt(K), "an abandoned loaded-chunk tile heals to its original");
        assertEquals(1, blocks.restores);
        assertTrue(ledger.isEmpty());

        // Diverged case: the tile was changed under us → heal drops the entry, restores nothing.
        FakeBlocks b2 = new FakeBlocks().seed(K, STONE);
        TempBlockLedger<Integer> l2 = new TempBlockLedger<>(b2);
        l2.place(K, MAGMA, 100, 0);
        b2.current.put(K, AIR);
        l2.healIfExpired(WORLD, 0, 64, 0, 100 + TempBlockLedger.STALE_GRACE_TICKS + 1);
        assertEquals(AIR, b2.typeAt(K));
        assertEquals(0, b2.restores, "a diverged abandoned tile restores nothing");
        assertTrue(l2.isEmpty());
    }

    // forEachTile visits exactly the live keys and never mutates.
    @Test
    void forEachTile_visitsExactlyLiveKeys() {
        Key k2 = new Key(WORLD, 5, 70, 3);
        FakeBlocks blocks = new FakeBlocks().seed(K, STONE).seed(k2, GOLD);
        TempBlockLedger<Integer> ledger = new TempBlockLedger<>(blocks);
        ledger.place(K, MAGMA, 100, 0);
        ledger.place(k2, ICE, 100, 0);

        List<Key> visited = new ArrayList<>();
        ledger.forEachTile((w, x, y, z) -> visited.add(new Key(w, x, y, z)));
        assertEquals(2, visited.size());
        assertTrue(visited.contains(K));
        assertTrue(visited.contains(k2));
        assertEquals(2, blocks.sets, "forEachTile is a pure read — only the two place() sets touched the world");
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
