package engine.interact;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SuppressionSetTest {

    @Test
    void addedIdsAreContainedOthersAreNot() {
        SuppressionSet s = new SuppressionSet();
        s.add(3);
        s.add(100);
        assertTrue(s.contains(3));
        assertTrue(s.contains(100));
        assertFalse(s.contains(4));
        assertFalse(s.contains(99));
    }

    @Test
    void negativeIdMeansNoKeyAndIsIgnored() {
        SuppressionSet s = new SuppressionSet();
        s.add(-1);
        assertFalse(s.contains(-1));
        assertTrue(s.isEmpty());
    }

    @Test
    void emptyUntilSomethingAdded() {
        SuppressionSet s = new SuppressionSet();
        assertTrue(s.isEmpty());
        s.add(0);
        assertFalse(s.isEmpty());
        assertTrue(s.contains(0));
    }

    @Test
    void clearResetsForReuse() {
        SuppressionSet s = new SuppressionSet();
        s.add(7);
        s.clear();
        assertFalse(s.contains(7));
        assertTrue(s.isEmpty());
    }
}
