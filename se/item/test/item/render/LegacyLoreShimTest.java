package item.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

/**
 * The one-time legacy-lore migration contract (ADR-0040): reconcile a pre-composer item's stored lore with the
 * freshly composed lore. Inputs are the test's own, so this pins the reconciliation rule, not any catalogue text.
 */
class LegacyLoreShimTest {

    private static final Predicate<String> LEGACY = line -> line.startsWith("MANAGED");

    @Test
    void keepsAuthoredHeadOnceAndDropsManagedAndDuplicateLines() {
        // existing pre-composer lore: an authored flavour line, a recognised legacy managed line, and a line the
        // fresh render already reproduces. Only the genuine authored line survives, above the composed sections.
        List<String> existing = List.of("authored flavour", "MANAGED trak 5", "enchant line");
        List<String> composed = List.of("enchant line", "PROTECTED");

        assertEquals(List.of("authored flavour", "enchant line", "PROTECTED"),
                LegacyLoreShim.migrate(existing, composed, LEGACY));
    }

    @Test
    void returnsComposedUnchangedWhenNothingAuthoredSurvives() {
        // Every existing line is either managed or reproduced by the fresh render → no head, so the composed lore
        // is returned as-is (the common gear case: lore is fully state-derived).
        List<String> composed = List.of("enchant line", "PROTECTED");
        List<String> existing = List.of("MANAGED trak 5", "enchant line", "PROTECTED");

        assertSame(composed, LegacyLoreShim.migrate(existing, composed, LEGACY));
    }
}
