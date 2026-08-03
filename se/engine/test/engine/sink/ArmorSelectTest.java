package engine.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * {@code DURABILITY select} — which worn piece a selector addresses, and the {@code skip-undamaged} filter that
 * keeps pristine gear out of the pick as well as the write. Damage arrays are test-owned fixtures in
 * {@code getArmorContents()} order (boots, leggings, chestplate, helmet); {@code -1} is an empty/non-wearing slot.
 */
class ArmorSelectTest {

    @ParameterizedTest(name = "{0} → slot {1}")
    @CsvSource({
            "whole-set, -1",
            "slot:boots, 0",
            "slot:leggings, 1",
            "slot:chestplate, 2",
            "slot:helmet, 3",
            "most-damaged, -2",
            "least-damaged, -3",
            "random-piece, -4",
            "not-a-selector, -1",
    })
    void everyAuthoredTokenMapsToItsCode(String token, int code) {
        assertEquals(code, ArmorSelect.of(token));
        assertEquals(code, ArmorSelect.of(token.toUpperCase(java.util.Locale.ROOT)), "tokens are case-insensitive");
    }

    @Test
    void extremesPickTheOnePieceAndIgnoreEmptySlots() {
        int[] damage = {12, -1, 40, 3}; // leggings slot empty
        assertEquals(2, ArmorSelect.pick(ArmorSelect.MOST_DAMAGED, damage, false, 0.0));
        assertEquals(3, ArmorSelect.pick(ArmorSelect.LEAST_DAMAGED, damage, false, 0.0));
    }

    @Test
    void skipUndamagedRemovesPristinePiecesFromTheCandidateSet() {
        int[] damage = {0, 0, 5, 0}; // only the chestplate has taken any wear
        assertEquals(2, ArmorSelect.pick(ArmorSelect.LEAST_DAMAGED, damage, true, 0.0),
                "the pristine pieces are not merely spared, they are not candidates");
        assertEquals(0, ArmorSelect.pick(ArmorSelect.LEAST_DAMAGED, damage, false, 0.0),
                "without the filter the least damaged is a pristine piece");
        assertEquals(2, ArmorSelect.pick(ArmorSelect.RANDOM_PIECE, damage, true, 0.99),
                "a scatter pick with one candidate always lands on it, whatever the draw");
    }

    @Test
    void randomPieceSpreadsAcrossEveryCandidateAndNeverOverruns() {
        int[] damage = {1, 2, 3, 4};
        assertEquals(0, ArmorSelect.pick(ArmorSelect.RANDOM_PIECE, damage, false, 0.0));
        assertEquals(1, ArmorSelect.pick(ArmorSelect.RANDOM_PIECE, damage, false, 0.3));
        assertEquals(3, ArmorSelect.pick(ArmorSelect.RANDOM_PIECE, damage, false, 0.99));
        assertEquals(3, ArmorSelect.pick(ArmorSelect.RANDOM_PIECE, damage, false, 1.0),
                "a draw at the open end of [0,1) still lands inside the candidate set");
    }

    @Test
    void nothingEligibleReportsNoneSoTheCallerWritesNothing() {
        int[] bare = {-1, -1, -1, -1};
        assertEquals(ArmorSelect.NONE, ArmorSelect.pick(ArmorSelect.MOST_DAMAGED, bare, false, 0.0));
        assertEquals(ArmorSelect.NONE, ArmorSelect.pick(ArmorSelect.RANDOM_PIECE, bare, false, 0.5));
        assertEquals(ArmorSelect.NONE, ArmorSelect.pick(ArmorSelect.HELMET, bare, false, 0.0));
        assertEquals(ArmorSelect.NONE, ArmorSelect.pick(ArmorSelect.HELMET, new int[] {0, 0, 0, 0}, true, 0.0),
                "a named slot obeys the filter too — a pristine helmet is left alone");
    }

    @Test
    void wholeSetIsTheCallerFastPathAndIsNeverFilteredAway() {
        assertEquals(ArmorSelect.WHOLE_SET,
                ArmorSelect.pick(ArmorSelect.WHOLE_SET, new int[] {0, 0, 0, 0}, true, 0.0));
    }

    @ParameterizedTest(name = "max {0}, amount {1}, percent {2} → {3} points")
    @CsvSource({
            "240, 5, 0, 5",      // flat: the authored point count, untouched
            "240, -1, 0, -1",    // the full-restore sentinel survives the flat path
            "240, 5, 2.5, 6",    // 2.5% of an iron chestplate, rounded
            "65, -1, 2.5, 2",    // the same authored percent bites proportionally on small gear
            "240, 5, 100, 240",  // a whole bar
    })
    void percentModesReadTheItemsOwnMaxDurability(int max, int amount, double percent, int points) {
        assertEquals(points, DispatchSinkBase.durabilityPoints(max, amount, percent));
    }
}
