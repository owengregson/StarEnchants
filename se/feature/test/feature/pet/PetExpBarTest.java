package feature.pet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.load.MasterConfig;
import org.junit.jupiter.api.Test;

/**
 * The {@link PetService#expBar} render algorithm (ADR-0052) as a format spec over test-owned {@code (level,
 * exp)} inputs and the default {@code pets:} knobs. Pins the max-level padding fix (1.8.4): a FULL bar keeps
 * the same right-hand pad every partial/empty bar shows — its last filled square carries its trailing space
 * before the template's closing bracket, so max-level formatting matches the other levels. The pad
 * assertions are glyph-agnostic (anchored on the {@code &7} empty-colour) so a change to the filled glyph is
 * not a false failure; {@link #fullBarStringIsExactlyPinned} additionally hard-pins the exact max-level
 * string (the regression the owner reported was the loss of that one trailing space).
 */
class PetExpBarTest {

    private static final MasterConfig.PetsSection CFG = MasterConfig.PetsSection.defaults();
    private static final String EMPTY_COLOUR = "&7"; // separates the filled group from the empty group

    @Test
    void fullBarKeepsItsRightHandPad() {
        String full = PetService.expBar(CFG.maxLevel(), 0, CFG);
        int closer = full.indexOf(EMPTY_COLOUR);
        assertTrue(closer > 0, "the full bar still carries the empty-colour marker");
        assertEquals(EMPTY_COLOUR, full.substring(closer), "no empty slots follow a full bar");
        assertEquals(' ', full.charAt(closer - 1),
                "a full bar keeps the trailing pad between its last filled square and the closer (max-level padding)");
    }

    @Test
    void fullBarStringIsExactlyPinned() {
        // The exact max-level render: ten filled squares, each with its trailing space, then the empty-colour
        // marker with no underscores following. The final "■ &7" is the restored pad (1.8.4 fix).
        assertEquals("&a■ ■ ■ ■ ■ ■ ■ ■ ■ ■ &7", PetService.expBar(CFG.maxLevel(), 0, CFG));
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
