package bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import feature.menu.ReferenceCatalog;
import item.codec.CombatState;
import item.codec.HeroicStat;
import item.lang.Messages;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import schema.diag.Diagnostic;
import schema.diag.Source;

/**
 * Pure unit tests for the {@code /se} diagnostic surfaces (server-free): {@code /se docs} routing,
 * {@code /se item dump} combat formatting, and {@code /se problems} diagnostic formatting. Each asserts the
 * decision/structure, feeding test-owned inputs rather than re-typing any shipped lang copy (writing-tests
 * Rule 1) — the templates come from {@link Messages#defaults()}.
 */
class SeCommandDiagnosticsTest {

    // ── /se docs routing ──
    @Test
    void docsRoutesKnownVocabulariesAndRejectsUnknown() {
        assertEquals(ReferenceCatalog.EFFECTS, SeCommand.docsCategory("effects"));
        assertEquals(ReferenceCatalog.CONDITIONS, SeCommand.docsCategory("CONDITIONS")); // case-insensitive
        assertEquals(ReferenceCatalog.TRIGGERS, SeCommand.docsCategory("triggers"));
        assertEquals(ReferenceCatalog.SELECTORS, SeCommand.docsCategory("selectors"));
        assertEquals(ReferenceCatalog.VARIABLES, SeCommand.docsCategory("variables"));
        assertNull(SeCommand.docsCategory("nonsense"));
    }

    @Test
    void everyDocVocabularyRoutesToARealCategory() {
        for (String vocab : SeCommand.DOC_VOCABS) {
            assertNull(SeCommand.docsCategory("no-" + vocab)); // sanity: the negatives don't route
            assertTrue(ReferenceCatalog.build().categories().contains(SeCommand.docsCategory(vocab)),
                    () -> vocab + " must route to a live ReferenceCatalog category");
        }
    }

    // ── /se item dump combat formatting ──
    @Test
    void dumpFormatsEnchantsSlotsCrystalsSetAndHeroic() {
        CombatState combat = new CombatState(
                Map.of("enchants/venom", 3, "enchants/vigor", 1),
                List.of("crystals/jolt"),
                "sets/titan", false,
                new HeroicStat(0.10, 0.20, 0.0), 2);
        String out = String.join("\n", SeCommand.combatLines(combat, 5, Messages.defaults()));

        assertTrue(out.contains("enchants/venom") && out.contains("3"), out); // key + level survive
        assertTrue(out.contains("crystals/jolt"), out);
        assertTrue(out.contains("sets/titan"), out);
        assertTrue(out.contains("2") && out.contains("7"), out); // added=2, max=base(5)+added(2)=7
        assertTrue(out.contains("10") && out.contains("20"), out); // heroic percents 10% dmg / 20% reduction
    }

    @Test
    void dumpOnAnEmptyStateStillReportsTheEmptySlots() {
        // A stripped item still gets a slot line (used 0 / max = base) — no enchant/crystal/set noise.
        List<String> lines = SeCommand.combatLines(CombatState.EMPTY, 9, Messages.defaults());
        String out = String.join("\n", lines);
        assertTrue(out.contains("9"), out); // base slots as the max
        assertEquals(3, lines.size(), out); // enchants-none + slots + crystals-none
    }

    // ── /se problems diagnostic formatting ──
    @Test
    void problemsReportsACleanLoadAsASingleLine() {
        List<String> lines = SeCommand.problemLines(List.of(), Messages.defaults());
        assertEquals(1, lines.size());
    }

    @Test
    void problemsSummarisesThenListsEachFinding() {
        Diagnostic error = Diagnostic.error("E_TESTONLY", "boom in a level", Source.of("enchants/x.yml", 3, 1));
        Diagnostic warning = Diagnostic.warning("W_TESTONLY", "harmless smell", Source.UNKNOWN);
        List<String> lines = SeCommand.problemLines(List.of(error, warning), Messages.defaults());

        assertEquals(3, lines.size()); // summary + one per finding
        assertTrue(lines.get(0).contains("1"), lines.get(0)); // 1 error / 1 warning
        String body = String.join("\n", lines);
        assertTrue(body.contains("E_TESTONLY") && body.contains("boom in a level"), body);
        assertTrue(body.contains("W_TESTONLY"), body);
    }
}
