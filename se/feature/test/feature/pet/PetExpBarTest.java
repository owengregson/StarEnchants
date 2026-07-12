package feature.pet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.load.MasterConfig;
import org.junit.jupiter.api.Test;

/**
 * The {@link PetService#expBar} render algorithm (ADR-0052) as a format spec over test-owned {@code (level,
 * exp)} inputs and the default {@code pets:} knobs. Pins the owner polish — a FULL bar sits flush against
 * the template's closing bracket (no trailing pad) — while asserting partial/empty bars keep their
 * right-hand pad. Assertions are glyph-agnostic (anchored on the {@code &7} empty-colour), so a change to
 * the filled glyph is not a false failure; a pad regression is.
 */
class PetExpBarTest {

    private static final MasterConfig.PetsSection CFG = MasterConfig.PetsSection.defaults();
    private static final String EMPTY_COLOUR = "&7"; // separates the filled group from the empty group

    @Test
    void fullBarSitsFlushAgainstTheCloser() {
        String full = PetService.expBar(CFG.maxLevel(), 0, CFG);
        int closer = full.indexOf(EMPTY_COLOUR);
        assertTrue(closer > 0, "the full bar still carries the empty-colour marker");
        assertEquals(EMPTY_COLOUR, full.substring(closer), "no empty slots follow a full bar");
        assertNotEquals(' ', full.charAt(closer - 1), "no pad between the last filled square and the closer");
    }

    @Test
    void partialBarKeepsItsSeparatorAndTrailingPad() {
        String partial = PetService.expBar(1, CFG.expPerLevel() / 2, CFG);
        int closer = partial.indexOf(EMPTY_COLOUR);
        // Unlike the full bar, a partial bar has an empty group following, so it KEEPS the separator space
        // between the last filled square and the closer, and the trailing pad after its last underscore.
        assertEquals(' ', partial.charAt(closer - 1), "a partial bar keeps the filled/empty separator space");
        assertTrue(partial.endsWith(" "), "a partial bar keeps the trailing pad on its underscore group");
    }

    @Test
    void emptyBarPadsBeforeItsFirstUnderscore() {
        String empty = PetService.expBar(1, 0, CFG);
        int closer = empty.indexOf(EMPTY_COLOUR);
        // Nothing filled: the empty-colour is immediately followed by a leading pad so the first underscore
        // does not hug the template's opening bracket.
        assertEquals(' ', empty.charAt(closer + EMPTY_COLOUR.length()), "empty bar pads a leading space");
    }
}
