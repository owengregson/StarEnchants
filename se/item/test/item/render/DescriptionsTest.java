package item.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link Descriptions} splits a '\n'-joined description into lore lines, keeping blank separator lines. */
class DescriptionsTest {

    @Test
    void blankYieldsNoLines() {
        assertTrue(Descriptions.lines(null).isEmpty());
        assertTrue(Descriptions.lines("").isEmpty());
        assertTrue(Descriptions.lines("   ").isEmpty());
    }

    @Test
    void singleLineIsOneLine() {
        assertEquals(List.of("just one line"), Descriptions.lines("just one line"));
    }

    @Test
    void splitsOnNewlineKeepingBlankSeparators() {
        assertEquals(List.of("&eIntro line", "", "&e&lI: 5%"), Descriptions.lines("&eIntro line\n\n&e&lI: 5%"));
    }
}
