package feature.soul;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The pure core of the multi-gem soul mechanic (no server): the zero-gem cleanup INVARIANT
 * ({@link SoulService#redundantZeroSlots}) and the least-souls drain-target selection
 * ({@link SoulService#leastNonzero}). These two decide every gem add/remove/retarget, so they carry the
 * mechanic's correctness; the inventory I/O around them is a thin shell verified live.
 */
class SoulGemSelectionTest {

    private static SoulService.GemView gem(int slot, int souls) {
        return new SoulService.GemView(slot, UUID.randomUUID(), souls);
    }

    // ── the zero-gem invariant: ≤1 zero gem, and only when it is the lone gem ───────────────────────

    @Test
    void noGemsOrNoZeroGemsRemovesNothing() {
        assertTrue(SoulService.redundantZeroSlots(List.of()).isEmpty());
        assertTrue(SoulService.redundantZeroSlots(List.of(gem(0, 5), gem(1, 9))).isEmpty());
    }

    @Test
    void aLoneZeroGemIsKept() {
        // never destroy the player's last gem, even at zero souls
        assertTrue(SoulService.redundantZeroSlots(List.of(gem(3, 0))).isEmpty());
    }

    @Test
    void everyZeroGemIsRemovedWhenAnyNonzeroGemExists() {
        // a zero gem may NOT coexist with a gem that still has souls
        assertEquals(List.of(0, 2), SoulService.redundantZeroSlots(List.of(gem(0, 0), gem(1, 5), gem(2, 0))));
    }

    @Test
    void onlyTheLowestSlotZeroGemSurvivesWhenAllAreZero() {
        // input order must not matter (sorted); slot 0 (lowest) is the lone survivor, 2 and 5 are removed
        assertEquals(List.of(2, 5), SoulService.redundantZeroSlots(List.of(gem(2, 0), gem(0, 0), gem(5, 0))));
    }

    // ── the drain target: least-souls nonzero gem, ties by lowest slot ──────────────────────────────

    @Test
    void picksTheLeastSoulsNonzeroGem() {
        // the 250-vs-500 case from the spec: drain the 250 (slot 1) first
        assertEquals(1, SoulService.leastNonzero(List.of(gem(0, 500), gem(1, 250), gem(2, 900))).orElseThrow().slot());
    }

    @Test
    void ignoresZeroGemsWhenChoosingTheTarget() {
        assertEquals(2, SoulService.leastNonzero(List.of(gem(0, 0), gem(1, 0), gem(2, 300))).orElseThrow().slot());
    }

    @Test
    void noTargetWhenNoGemHasSouls() {
        assertTrue(SoulService.leastNonzero(List.of(gem(0, 0))).isEmpty());
        assertTrue(SoulService.leastNonzero(List.of()).isEmpty());
    }

    @Test
    void tiesAreBrokenByLowestSlot() {
        assertEquals(1, SoulService.leastNonzero(List.of(gem(3, 100), gem(1, 100), gem(5, 100))).orElseThrow().slot());
    }
}
