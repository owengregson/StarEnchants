package schema.grammar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import schema.diag.Source;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The inline {@code @Head{...}} selector peeled off an effect line's trailing segment. */
class EffectLineSelectorTest {

    private static final Source SRC = Source.of("enchants.yml", 5, 1);

    @Test
    void trailingSelectorSegmentIsPeeledFromArgs() {
        EffectLine line = EffectLine.parse("DAMAGE:6:@Aoe{r=4}", SRC);
        assertEquals("DAMAGE", line.head());
        assertEquals(List.of("6"), line.argTexts());
        assertEquals(1, line.argCount());
        assertTrue(line.selectorToken().isPresent());
        assertEquals("@Aoe{r=4}", line.selectorToken().get());
    }

    @Test
    void bareTrailingSelectorIsRecognised() {
        EffectLine line = EffectLine.parse("HEAL:3:@Self", SRC);
        assertEquals(List.of("3"), line.argTexts());
        assertEquals("@Self", line.selectorToken().orElseThrow());
    }

    @Test
    void selectorBodyColonsSurviveTheArgSplit() {
        // The selector body's braces protect inner colons from the top-level ':' split.
        EffectLine line = EffectLine.parse("MSG:hi:@Nearest{a=1:2}", SRC);
        assertEquals(List.of("hi"), line.argTexts());
        assertEquals("@Nearest{a=1:2}", line.selectorToken().orElseThrow());
    }

    @Test
    void noSelectorWhenNoTrailingAtToken() {
        EffectLine line = EffectLine.parse("DAMAGE:3:5", SRC);
        assertFalse(line.selectorToken().isPresent());
        assertEquals(List.of("3", "5"), line.argTexts());
        assertEquals(SRC, line.selectorSource());
    }

    @Test
    void selectorSourcePointsAtTheSelectorColumn() {
        EffectLine line = EffectLine.parse("DAMAGE:6:@Aoe{r=4}", SRC);
        // "DAMAGE:6:" is 9 chars, so the selector starts at column 10.
        assertEquals(10, line.selectorSource().col());
    }
}
