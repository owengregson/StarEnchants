package feature.scroll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for {@link ScrollService#reorderedEnchants} — the permutation guard behind the Godly
 * Transmog reorder GUI, exercised without a server. The live suite proves the write + lore re-render; this
 * pins that a non-permutation (missing/extra/duplicate key) is refused so an enchant is never dropped or
 * duplicated, and that a valid permutation yields exactly the requested order with the right levels.
 */
class ScrollServiceReorderTest {

    private static Map<String, Integer> current() {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("enchants/a", 1);
        m.put("enchants/b", 3);
        m.put("enchants/c", 2);
        return m;
    }

    @Test
    void aPermutationReordersAndPreservesLevels() {
        Optional<Map<String, Integer>> out =
                ScrollService.reorderedEnchants(current(), List.of("enchants/c", "enchants/a", "enchants/b"));
        assertTrue(out.isPresent());
        assertEquals(List.of("enchants/c", "enchants/a", "enchants/b"), List.copyOf(out.get().keySet()));
        assertEquals(2, out.get().get("enchants/c"));
        assertEquals(1, out.get().get("enchants/a"));
        assertEquals(3, out.get().get("enchants/b"));
    }

    @Test
    void aMissingKeyIsRefused() {
        assertTrue(ScrollService.reorderedEnchants(current(), List.of("enchants/a", "enchants/b")).isEmpty());
    }

    @Test
    void anExtraOrUnknownKeyIsRefused() {
        assertTrue(ScrollService.reorderedEnchants(current(),
                List.of("enchants/a", "enchants/b", "enchants/c", "enchants/d")).isEmpty());
        assertTrue(ScrollService.reorderedEnchants(current(),
                List.of("enchants/a", "enchants/b", "enchants/x")).isEmpty());
    }

    @Test
    void aDuplicateKeyIsRefused() {
        // Same size, but a duplicate means a dropped enchant — must be refused, not silently lose one.
        assertTrue(ScrollService.reorderedEnchants(current(),
                List.of("enchants/a", "enchants/a", "enchants/b")).isEmpty());
    }
}
