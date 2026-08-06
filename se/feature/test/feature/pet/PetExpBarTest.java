package feature.pet;

import static org.junit.jupiter.api.Assertions.assertEquals;

import compile.load.MasterConfig;
import org.junit.jupiter.api.Test;

/**
 * The {@link PetService#expBar} render algorithm (ADR-0052, widened by R-QC65) as a format spec over
 * test-owned {@code (level, exp)} inputs and the default {@code pets:} knobs.
 *
 * <p>The bar is FIFTY segments, one glyph each and no separator — the recorded width, restored over the
 * proportionality the port always had. What is pinned here is the arithmetic that made the jar's own bar
 * useless: it must fill in proportion below the cap (the jar's long division floored every partial bar to
 * zero) and clamp at exactly full rather than overflow past its width (the jar grew the line unbounded once
 * banked exp passed the threshold). The segment count is read from the rendered string, so the two halves of
 * "50 wide" and "fills proportionally" are one assertion.
 */
class PetExpBarTest {

    private static final MasterConfig.PetsSection CFG = MasterConfig.PetsSection.defaults();
    private static final int SEGMENTS = 50;

    /** Filled segments of a rendered bar — everything before the empty-colour marker, glyphs only. */
    private static int filled(String bar) {
        return bar.substring(0, bar.indexOf("&c")).length() - "&a".length();
    }

    /** Total segments of a rendered bar, so a width change fails here rather than silently halving the meter. */
    private static int width(String bar) {
        return bar.length() - "&a".length() - "&c".length();
    }

    @Test
    void aCappedPetRendersExactlyFullAndNeverWider() {
        // The jar overflowed here: past the threshold its `exp/needed` became 2, 3, 4… and the line grew to
        // 100 and 150 glyphs. Banked exp far beyond the threshold must still render one full bar.
        String full = PetService.expBar(CFG.maxLevel(), CFG.expPerLevel() * 9, CFG);
        assertEquals(SEGMENTS, filled(full), "a capped pet is full");
        assertEquals(SEGMENTS, width(full), "and never wider than the bar");
    }

    @Test
    void aPartialBarFillsInProportionRatherThanFlooringToEmpty() {
        // The bug D-12-2 records: integer division made every non-capped bar read empty at every XP value.
        assertEquals(SEGMENTS / 2, filled(PetService.expBar(1, CFG.expPerLevel() / 2, CFG)), "half is half");
        assertEquals(SEGMENTS / 5, filled(PetService.expBar(1, CFG.expPerLevel() / 5, CFG)));
        assertEquals(SEGMENTS, width(PetService.expBar(1, CFG.expPerLevel() / 2, CFG)));
    }

    @Test
    void anUnstartedLevelIsEmptyAndAFinishedOneIsFullWithoutCrossingTheLevelBoundary() {
        assertEquals(0, filled(PetService.expBar(1, 0, CFG)), "no progress renders no fill");
        assertEquals(SEGMENTS, width(PetService.expBar(1, 0, CFG)));
        // Exp at (or, through a curved pet's own threshold, above) the requirement clamps at full rather than
        // spilling — the level roll is what advances the level, not the meter.
        assertEquals(SEGMENTS, filled(PetService.expBar(1, CFG.expPerLevel(), CFG)));
        assertEquals(SEGMENTS, filled(PetService.expBar(1, CFG.expPerLevel() * 3, CFG)));
    }
}
