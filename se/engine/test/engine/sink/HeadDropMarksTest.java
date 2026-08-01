package engine.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class HeadDropMarksTest {

    @AfterEach
    void clear() {
        HeadDropMarks.clearAll();
    }

    @Test
    void channelsDeduplicateIndependentlyAndAreConsumedOnce() {
        UUID player = UUID.randomUUID();
        HeadDropMarks.mark(player, "headless");
        HeadDropMarks.mark(player, "headless");
        HeadDropMarks.mark(player, "decapitation");

        assertEquals(2, HeadDropMarks.consume(player));
        assertEquals(0, HeadDropMarks.consume(player));
    }
}
