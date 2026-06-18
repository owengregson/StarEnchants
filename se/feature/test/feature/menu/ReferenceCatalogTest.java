package feature.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for {@link ReferenceCatalog} — that the reference browser enumerates all five runtime
 * vocabularies from the live registries (no server). Pins the category set, that none is empty, and a few
 * stable members, so a registry wiring regression (e.g. the variable-enumeration accessor breaking) fails
 * here rather than silently showing a blank category in-game.
 */
class ReferenceCatalogTest {

    private static final ReferenceCatalog CATALOG = ReferenceCatalog.build();

    private static List<String> titles(String category) {
        return CATALOG.entries(category).stream().map(ReferenceCatalog.Entry::title).toList();
    }

    @Test
    void exposesAllFiveCategoriesInOrder() {
        assertEquals(List.of(ReferenceCatalog.EFFECTS, ReferenceCatalog.SELECTORS, ReferenceCatalog.TRIGGERS,
                ReferenceCatalog.CONDITIONS, ReferenceCatalog.VARIABLES), CATALOG.categories());
    }

    @Test
    void everyCategoryHasEntries() {
        for (String category : CATALOG.categories()) {
            assertFalse(CATALOG.entries(category).isEmpty(), category + " category must enumerate entries");
        }
    }

    @Test
    void effectsAndSelectorsHaveNonBlankTitles() {
        for (ReferenceCatalog.Entry e : CATALOG.entries(ReferenceCatalog.EFFECTS)) {
            assertFalse(e.title().isBlank(), "every effect entry has a head");
        }
        assertFalse(CATALOG.entries(ReferenceCatalog.SELECTORS).isEmpty());
    }

    @Test
    void triggersIncludeAttack() {
        assertTrue(titles(ReferenceCatalog.TRIGGERS).contains("ATTACK"),
                "the trigger vocabulary must list ATTACK");
    }

    @Test
    void conditionsListTheOperatorVocabulary() {
        List<String> ops = titles(ReferenceCatalog.CONDITIONS);
        assertTrue(ops.contains("=="), "relational operators are listed");
        assertTrue(ops.contains("contains"), "string operators are listed");
        assertTrue(ops.contains("matchesregex"), "string operators are listed");
    }

    @Test
    void variablesAreEnumeratedAsScopedFacts() {
        List<String> vars = titles(ReferenceCatalog.VARIABLES);
        assertFalse(vars.isEmpty(), "the variable vocabulary must enumerate (the bindings() accessor)");
        for (String v : vars) {
            assertTrue(v.startsWith("%") && v.endsWith("%"), "variable rendered as %scope.name%: " + v);
        }
    }
}
