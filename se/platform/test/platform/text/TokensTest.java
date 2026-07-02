package platform.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link Tokens} — underscore-canonical substitution with the hyphen alias and the line-expansion rule. */
final class TokensTest {

    @Test
    void subFillsPairs() {
        assertEquals("1 x", Tokens.sub("{A} {B}", "A", 1, "B", "x"));
    }

    @Test
    void underscoreKeyAlsoFillsHyphenAlias() {
        assertEquals("&e&e", Tokens.sub("{TIER_COLOR}{TIER-COLOR}", "TIER_COLOR", "&e"));
    }

    @Test
    void aliasOnlyWhenKeyHasUnderscore() {
        // A key carrying no '_' never aliases to a hyphen spelling.
        assertEquals("{SOUL_COLOR}", Tokens.sub("{SOUL_COLOR}", "SOUL-COLOR", "&c"));
        // The underscore key DOES fill its hyphen alias.
        assertEquals("&c", Tokens.sub("{SOUL-COLOR}", "SOUL_COLOR", "&c"));
    }

    @Test
    void nullAndTokenlessAndOddTail() {
        assertNull(Tokens.sub(null, "A", 1));
        assertEquals("plain", Tokens.sub("plain", "A", 1));
        assertEquals("1", Tokens.sub("{A}", "A", 1, "B")); // odd kv tail ignored
    }

    @Test
    void subLinesMapsPerLine() {
        assertEquals(List.of("1", "x1"), Tokens.subLines(List.of("{A}", "x{A}"), "A", 1));
    }

    @Test
    void expandLinesEmitsOnePerElement() {
        List<String> out = Tokens.expandLines(
                List.of("a", "{TIER_COLOR}{DESCRIPTION}", "c"), "DESCRIPTION", List.of("l1", "l2"),
                "TIER_COLOR", "&e");
        assertEquals(List.of("a", "&el1", "&el2", "c"), out);
    }

    @Test
    void expandLinesDropsLineOnEmptyExpansion() {
        List<String> out = Tokens.expandLines(
                List.of("a", "{DESCRIPTION}", "c"), "DESCRIPTION", List.of());
        assertEquals(List.of("a", "c"), out);
    }

    @Test
    void expandLinesIgnoresExpansionWhenTokenAbsent() {
        List<String> out = Tokens.expandLines(
                List.of("a", "b"), "DESCRIPTION", List.of("l1", "l2"));
        assertEquals(List.of("a", "b"), out);
    }
}
