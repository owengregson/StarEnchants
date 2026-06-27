package item.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Pure word-wrap tests for {@link TextWrap} — no server. */
class TextWrapTest {

    @Test
    void emptyOrNullYieldsNoLines() {
        assertEquals(List.of(), TextWrap.wrap(null, 30));
        assertEquals(List.of(), TextWrap.wrap("", 30));
    }

    @Test
    void shortTextIsOneLine() {
        assertEquals(List.of("hello there"), TextWrap.wrap("hello there", 30));
    }

    @Test
    void wrapsAtTheVisibleWidthOnWordBoundaries() {
        // 12 visible chars per line; "the quick" (9) fits, "+brown" would be 15 → wrap.
        List<String> out = TextWrap.wrap("the quick brown fox", 12);
        assertEquals(List.of("the quick", "brown fox"), out);
    }

    @Test
    void colourCodesDoNotCountTowardWidthAndCarryToContinuationLines() {
        // 5 visible chars; the &a colour is free width and re-emitted on the wrapped continuation.
        List<String> out = TextWrap.wrap("&aone two three", 5);
        assertEquals(List.of("&aone", "&atwo", "&athree"), out);
    }

    @Test
    void colourThenFormatBothCarryToContinuationLines() {
        // colour (&a) THEN bold (&l) = bold yellow; the wrapped continuation re-emits both (&a&l).
        List<String> out = TextWrap.wrap("&a&lalpha bravo", 5);
        assertEquals(List.of("&a&lalpha", "&a&lbravo"), out);
    }

    @Test
    void hardNewlinesAreHonouredAndBlankLinesPreserved() {
        List<String> out = TextWrap.wrap("a\n\nb", 30);
        assertEquals(List.of("a", "", "b"), out);
    }

    @Test
    void aWordLongerThanTheWidthIsNotSplit() {
        List<String> out = TextWrap.wrap("supercalifragilistic ok", 5);
        assertEquals(List.of("supercalifragilistic", "ok"), out);
    }

    @Test
    void nonPositiveWidthDisablesWrapping() {
        assertEquals(List.of("a long line that would wrap"), TextWrap.wrap("a long line that would wrap", 0));
    }

    @Test
    void visibleLengthIgnoresColourCodes() {
        assertEquals(3, TextWrap.visibleLength("&aabc"));
        assertEquals(3, TextWrap.visibleLength("§aabc"));
        assertEquals(5, TextWrap.visibleLength("&l&ahello"));
        assertTrue(TextWrap.visibleLength("&a&b&c") == 0);
    }
}
