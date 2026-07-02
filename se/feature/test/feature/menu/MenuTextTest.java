package feature.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.bukkit.ChatColor;
import org.junit.jupiter.api.Test;
import platform.caps.Capabilities;

/**
 * Pure unit tests for {@link MenuText} — colour translation and the cross-version inventory-title length
 * cap. The 32-char {@code String}-title limit applies pre-1.20 (a longer title garbles/throws on the floor)
 * and was lifted at 1.20; this pins the version gate and that truncation never leaves a dangling {@code §}.
 */
class MenuTextTest {

    private static final Capabilities FLOOR = Capabilities.probe("1.17.1", false);
    private static final Capabilities MODERN = Capabilities.probe("1.20.0", false);

    @Test
    void translatesLegacyAmpersandColourCodes() {
        assertEquals(ChatColor.AQUA + "Star" + ChatColor.RED + "Enchants",
                MenuText.title("&bStar&cEnchants", MODERN));
    }

    @Test
    void preTwentyTruncatesToThirtyTwoCharacters() {
        String long40 = "x".repeat(40);
        assertEquals(32, MenuText.title(long40, FLOOR).length());
    }

    @Test
    void modernKeepsTheFullTitle() {
        String long40 = "x".repeat(40);
        assertEquals(40, MenuText.title(long40, MODERN).length());
    }

    @Test
    void truncationNeverEndsOnALoneColourChar() {
        // 31 plain chars, then a colour code: the naive cut at 32 would land on the bare '§'.
        String raw = "a".repeat(31) + "&cmore";
        String cut = MenuText.title(raw, FLOOR);
        assertFalse(cut.isEmpty());
        assertTrue(cut.charAt(cut.length() - 1) != ChatColor.COLOR_CHAR,
                "truncated title must not end on a dangling section sign");
        assertEquals(31, cut.length()); // the trailing '§' was dropped rather than kept half-formed
    }

    @Test
    void nullCapsDegradesToTheConservativeCap() {
        // A pure context with no probe (caps == null) must still apply the safe 32-char cap.
        assertEquals(32, MenuText.title("y".repeat(50), null).length());
    }

    @Test
    void describeSplitsEachDescriptionLineAndPrefixesIt() {
        // A multi-line description becomes one lore entry PER line (so the newlines render), each carrying the
        // default colour — a blank separator line is kept (becomes just the colour code).
        assertEquals(List.of("&7&eIntro", "&7", "&7&e&lI: 5%"),
                MenuText.describe("&eIntro\n\n&e&lI: 5%", "&7"));
    }

    @Test
    void describeIsEmptyForABlankDescription() {
        assertTrue(MenuText.describe("", "&7").isEmpty());
        assertTrue(MenuText.describe(null, "&7").isEmpty());
    }
}
