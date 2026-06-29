package item.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.load.ScrollsConfig;
import item.mint.ItemFactory;
import org.junit.jupiter.api.Test;

/**
 * The enchant-count name suffix transform (re-homed from {@code ScrollCountSuffixTest} when the suffix became
 * an always-on, render-stamped name decoration). Pins (a) the Bukkit colour-normalisation strip trap that let a
 * re-stamp STACK its count ({@code [2] [2]}), and (b) the new {@link EnchantCountSuffix#nameFor} contract:
 * append only when the count is positive, replace-not-stack, and STRIP entirely at zero (a drop to no custom
 * enchants removes the tag). The template is read from production defaults — never re-typed; {@code "§bMy
 * Blade"} is the test's own input (writing-tests Rule 1).
 */
class EnchantCountSuffixTest {

    private static final String TEMPLATE = ScrollsConfig.defaults().transmog().nameSuffix();
    private static final String BASE = "§bMy Blade";

    private static String stamped(int count) {
        return BASE + ItemFactory.color(TEMPLATE.replace(EnchantCountSuffix.COUNT_PLACEHOLDER, Integer.toString(count)));
    }

    @Test
    void stripsItsOwnSuffixIncludingTheBukkitNormalisedForm() {
        String raw = stamped(2);
        // Bukkit's ItemMeta collapses a redundant §r immediately before a colour (§r§d -> §d) on set→get;
        // simulate that so the unit test reproduces what the live server actually stores.
        String normalised = raw.replace("§r§d", "§d");

        assertEquals(BASE, EnchantCountSuffix.strip(raw, TEMPLATE), "must strip the un-normalised suffix");
        assertEquals(BASE, EnchantCountSuffix.strip(normalised, TEMPLATE),
                "must ALSO strip the Bukkit-normalised suffix — else a re-stamp stacks it");
        assertEquals(BASE, EnchantCountSuffix.strip(BASE, TEMPLATE), "a name with no suffix is unchanged");
    }

    @Test
    void nameForAppendsTheSuffixWhenCountIsPositive() {
        assertEquals(stamped(3), EnchantCountSuffix.nameFor(BASE, TEMPLATE, 3));
    }

    @Test
    void nameForReplacesRatherThanStacksOnReStamp() {
        // a name that already carries [2] re-stamps to [5], not "[2] [5]"
        assertEquals(stamped(5), EnchantCountSuffix.nameFor(stamped(2), TEMPLATE, 5));
    }

    @Test
    void nameForStripsTheSuffixAtZeroCount() {
        // dropping to zero custom enchants removes the tag, leaving the bare base name
        assertEquals(BASE, EnchantCountSuffix.nameFor(stamped(4), TEMPLATE, 0));
    }

    @Test
    void nameForReturnsEmptyForNoBaseAndZeroCount() {
        assertTrue(EnchantCountSuffix.nameFor(null, TEMPLATE, 0).isEmpty());
    }

    @Test
    void aBlankOrNullTemplateLeavesTheNameUntouched() {
        assertEquals(BASE, EnchantCountSuffix.nameFor(BASE, "", 3));
        assertEquals(BASE, EnchantCountSuffix.nameFor(BASE, null, 3));
    }
}
