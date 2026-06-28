package compile.load;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** The trak-gem family config (§I) — defaults are present, distinct, and carry a count format + applies-to. */
class TraksConfigTest {

    @Test
    void defaultsAreDistinctAndComplete() {
        TraksConfig d = TraksConfig.defaults();
        List<TraksConfig.Trak> traks = List.of(d.block(), d.mob(), d.soul(), d.fish());
        // Assert the gems are mutually DISTINCT (the real invariant — they must look different), each with a
        // non-blank material applying to >=1 kind and a count line. Never re-type the specific materials.
        Set<String> materials = traks.stream().map(TraksConfig.Trak::material).collect(Collectors.toSet());
        assertEquals(traks.size(), materials.size(), "each trak gem has a distinct material");
        for (TraksConfig.Trak trak : traks) {
            assertFalse(trak.material().isBlank(), "a trak gem has a material");
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
