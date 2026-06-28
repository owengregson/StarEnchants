package feature.scroll;

import static org.junit.jupiter.api.Assertions.assertEquals;

import compile.load.ScrollsConfig;
import item.mint.ItemFactory;
import org.junit.jupiter.api.Test;

/**
 * Pins {@link ScrollService#stripCountSuffix} against the Bukkit colour-normalisation trap that let a
 * re-transmog STACK its count suffix ({@code [2] [2]}). The live transmog suite caught the end-to-end bug;
 * this nails the strip at the unit layer, where it is pure regex logic (the normalised input is simulated, so
 * no server is needed). The suffix template is read from production defaults — never re-typed; {@code "§bMy
 * Blade"} is the test's own input (writing-tests Rule 1).
 */
class ScrollCountSuffixTest {

    private static final String TEMPLATE = ScrollsConfig.defaults().transmog().nameSuffix();
    private static final String BASE = "§bMy Blade";

    @Test
    void stripsItsOwnSuffixIncludingTheBukkitNormalisedForm() {
        String stampedRaw = BASE + ItemFactory.color(TEMPLATE.replace(ScrollService.COUNT_PLACEHOLDER, "2"));
        // Bukkit's ItemMeta collapses a redundant §r immediately before a colour (§r§d -> §d) on set→get;
        // simulate that so the unit test reproduces what the live server actually stores.
        String stampedNormalised = stampedRaw.replace("§r§d", "§d");

        assertEquals(BASE, ScrollService.stripCountSuffix(stampedRaw, TEMPLATE),
                "must strip the un-normalised stamped suffix");
        assertEquals(BASE, ScrollService.stripCountSuffix(stampedNormalised, TEMPLATE),
                "must ALSO strip the Bukkit-normalised suffix — else a re-apply stacks it");
        assertEquals(BASE, ScrollService.stripCountSuffix(BASE, TEMPLATE),
                "a name with no suffix is returned unchanged");
    }
}
