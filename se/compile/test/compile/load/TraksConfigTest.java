package compile.load;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The trak-gem family config (§I) — defaults are present, distinct, and carry a count format + applies-to. */
class TraksConfigTest {

    @Test
    void defaultsAreDistinctAndComplete() {
        TraksConfig d = TraksConfig.defaults();
        assertEquals("SLIME_BALL", d.block().material());
        assertEquals("MAGMA_CREAM", d.mob().material());
        assertEquals("FIRE_CHARGE", d.soul().material());
        assertEquals("CLAY_BALL", d.fish().material());
        for (TraksConfig.Trak trak : List.of(d.block(), d.mob(), d.soul(), d.fish())) {
            assertFalse(trak.appliesTo().isEmpty(), "a trak gem applies to at least one kind");
            assertTrue(trak.countFormat().contains("{COUNT}"), "the count format carries the {COUNT} placeholder");
        }
    }

    @Test
    void copiesAreDefensive() {
        TraksConfig.Trak t = new TraksConfig.Trak("M", "n", List.of("a"), List.of("TOOL"), "x {COUNT}");
        assertEquals(List.of("a"), t.lore());
        assertEquals(List.of("TOOL"), t.appliesTo());
    }
}
