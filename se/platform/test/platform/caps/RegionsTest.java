package platform.caps;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The one cross-region guard: fall back on a throwing body, and never rethrow on Paper (log-only). */
class RegionsTest {

    @AfterEach
    void restore() {
        Regions.install(Capabilities.foliaPresent());
    }

    @Test
    void readReturnsTheBodyValue() {
        assertEquals(42, Regions.read("t", () -> 42, 0));
    }

    @Test
    void readFallsBackOnFoliaMode() {
        Regions.install(true);
        assertEquals(7, Regions.read("t", () -> {
            throw new IllegalStateException("cross-region");
        }, 7));
    }

    @Test
    void readFallsBackAndDoesNotRethrowOnPaperMode() {
        Regions.install(false);
        // The contract on Paper is log-don't-rethrow: a real bug is surfaced to the log, not thrown mid-combat.
        assertEquals(7, Regions.read("t", () -> {
            throw new IllegalStateException("real bug");
        }, 7));
    }

    @Test
    void swallowedNeverThrowsInEitherMode() {
        Regions.install(true);
        assertDoesNotThrow(() -> Regions.swallowed("t", new IllegalStateException("x")));
        Regions.install(false);
        assertDoesNotThrow(() -> Regions.swallowed("t", new IllegalStateException("x")));
    }
}
