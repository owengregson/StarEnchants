package engine.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import engine.condition.GroundOwnership;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Ground ownership for the packet-only {@code PHANTOM_BLOCKS} overlay. The bug this closes: the overlay wrote
 * no block, so {@code TempBlockLedger} had never heard of the patch, and {@code STACKING_DOT}'s per-pulse
 * "whose ground am I standing on?" found nobody — Rot and Decay's entire ramping DECAYING half armed, re-read
 * the ground every pulse and did nothing, for its whole window, forever.
 */
class PhantomFieldsTest {

    private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-000000000a1d");
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000a11c");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000b0bb");

    /** A 2x1 patch at y=63 — two columns, so a broken per-position loop cannot pass on the first one. */
    private static List<int[]> patch() {
        return List.of(new int[] {0, 63, 0}, new int[] {1, 63, 0});
    }

    @Test
    void aClaimedPatchIsOwnedGroundUntilItsWindowLapses() {
        PhantomFields fields = new PhantomFields();
        assertNull(fields.ownerAt(WORLD, 0, 63, 0, 0L), "unclaimed ground belongs to nobody");

        fields.claim(WORLD, ALICE, 100L, patch(), 0L);
        assertEquals(ALICE, fields.ownerAt(WORLD, 0, 63, 0, 0L));
        assertEquals(ALICE, fields.ownerAt(WORLD, 1, 63, 0, 99L), "every column of the patch is claimed");
        assertNull(fields.ownerAt(WORLD, 2, 63, 0, 0L), "and nothing outside it is");
        assertNull(fields.ownerAt(WORLD, 0, 64, 0, 0L), "the claim is the surface block, not the air above it");

        // The deadline is authoritative, not advisory: a release whose region task never ran must not leave a
        // patch owned forever — the DoT would keep ramping on ground that stopped existing.
        assertNull(fields.ownerAt(WORLD, 0, 63, 0, 100L), "a lapsed window owns nothing");
    }

    @Test
    void releasingOneFieldLeavesALaterOverlapsClaimStanding() {
        // Two fields over one patch: the later painter owns it (the ledger's same-material refresh rule), and
        // the EARLIER one closing must not blank ground it no longer holds.
        PhantomFields fields = new PhantomFields();
        long first = fields.claim(WORLD, ALICE, 500L, patch(), 0L);
        fields.claim(WORLD, BOB, 500L, patch(), 0L);
        assertEquals(BOB, fields.ownerAt(WORLD, 0, 63, 0, 10L));

        fields.release(first);
        assertEquals(BOB, fields.ownerAt(WORLD, 0, 63, 0, 10L), "the live field keeps its ground");
    }

    @Test
    void releaseDropsTheClaimAndTheRegistryEmpties() {
        PhantomFields fields = new PhantomFields();
        long id = fields.claim(WORLD, ALICE, 500L, patch(), 0L);
        assertFalse(fields.isEmpty());

        fields.release(id);
        assertNull(fields.ownerAt(WORLD, 0, 63, 0, 10L));
        assertTrue(fields.isEmpty(), "a closed window leaves nothing behind to leak");
    }

    @Test
    void aNewClaimSweepsFieldsWhoseReleaseNeverRan() {
        // The dropped-region-task reaper: a Folia chunk unload can eat the release, and the entries would sit
        // in the map for the life of the boot. They answer null already; this proves they are also reclaimed.
        PhantomFields fields = new PhantomFields();
        fields.claim(WORLD, ALICE, 100L, patch(), 0L);
        fields.claim(WORLD, BOB, 400L, List.of(new int[] {9, 63, 9}), 300L);

        assertNull(fields.ownerAt(WORLD, 0, 63, 0, 300L));
        assertEquals(BOB, fields.ownerAt(WORLD, 9, 63, 9, 300L));
        fields.release(2L); // the surviving field's id — if the lapsed one was not swept, the map is not empty
        assertTrue(fields.isEmpty());
    }

    @Test
    void theComposedReadAnswersForAPlacedFloorAndAPhantomPatchAlike() {
        // The single-sourced lookup %actor.ownedground% and STACKING_DOT share. Both sources must answer, and
        // ownership must stay per-player: standing in someone ELSE's field is not standing in your own.
        FakeBlocks blocks = new FakeBlocks();
        TempBlockLedger<Integer> placed = new TempBlockLedger<>(blocks);
        PhantomFields fields = new PhantomFields();
        long[] now = {0L};
        GroundOwnership ground = PhantomFields.over(placed, fields, () -> now[0]);

        assertFalse(ground.ownedBy(ALICE, WORLD, 0, 63, 0), "no field anywhere");

        fields.claim(WORLD, ALICE, 100L, patch(), 0L);
        assertTrue(ground.ownedBy(ALICE, WORLD, 0, 63, 0), "a packet-only patch IS her ground");
        assertFalse(ground.ownedBy(BOB, WORLD, 0, 63, 0), "but only hers");

        now[0] = 100L;
        assertFalse(ground.ownedBy(ALICE, WORLD, 0, 63, 0), "the read is live against the window, not the arm");

        placed.place(new TempBlockLedger.Key(WORLD, 5, 63, 5), 7, 100, now[0], BOB);
        assertTrue(ground.ownedBy(BOB, WORLD, 5, 63, 5), "a REAL placement still answers through the same read");
        assertFalse(ground.ownedBy(null, WORLD, 5, 63, 5), "an ownerless ask is never satisfied");
    }

    /** The minimal {@link TempBlockLedger.BlockOps} the composed-read case needs (no world, no Bukkit). */
    private static final class FakeBlocks implements TempBlockLedger.BlockOps<Integer> {

        private int type = 1;

        @Override
        public int readTypeId(TempBlockLedger.Key key) {
            return type;
        }

        @Override
        public void setTypeId(TempBlockLedger.Key key, int typeId) {
            type = typeId;
        }

        @Override
        public Integer captureOriginal(TempBlockLedger.Key key) {
            return type;
        }

        @Override
        public void restoreOriginal(TempBlockLedger.Key key, Integer original) {
            type = original;
        }
    }
}
