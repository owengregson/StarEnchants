package bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import feature.menu.ReferenceCatalog;
import item.codec.CombatState;
import item.codec.HeroicStat;
import platform.lang.Messages;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import schema.diag.Diagnostic;
import schema.diag.Severity;
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
        Diagnostic error = new Diagnostic(Severity.ERROR, "E_TESTONLY", "boom in a level",
                Source.of("enchants/x.yml", 3, 1), null);
        Diagnostic warning = new Diagnostic(Severity.WARNING, "W_TESTONLY", "harmless smell", Source.UNKNOWN, null);
        List<String> lines = SeCommand.problemLines(List.of(error, warning), Messages.defaults());

        assertEquals(3, lines.size()); // summary + one per finding
        assertTrue(lines.get(0).contains("1"), lines.get(0)); // 1 error / 1 warning
        String body = String.join("\n", lines);
        assertTrue(body.contains("E_TESTONLY") && body.contains("boom in a level"), body);
        assertTrue(body.contains("W_TESTONLY"), body);
    }

    // ── /se pack apply gate rendering (ADR-0046) ──

    @Test
    void gateMatchCleanIsASingleLineWithTheAbilityCount() {
        PackGate.Report report = new PackGate.Report(
                PackGate.FingerprintStatus.MATCH, "1:live", "1:live", 42, List.of());
        List<String> lines = SeCommand.gateLines(report, Messages.defaults(), "cosmic", false);
        assertEquals(1, lines.size()); // MATCH → no explanation line; clean → one gate-clean line
        assertTrue(lines.get(0).contains("42"), lines.get(0)); // the would-load ability count
    }

    @Test
    void gateMismatchErrorsNoForceRendersEveryBlockingLineThenAbort() {
        Diagnostic e1 = new Diagnostic(Severity.ERROR, "E_ONE", "first boom", Source.of("enchants/a.yml", 3, 1), null);
        Diagnostic e2 = new Diagnostic(Severity.ERROR, "E_TWO", "second boom", Source.of("enchants/b.yml", 5, 2), null);
        PackGate.Report report = new PackGate.Report(
                PackGate.FingerprintStatus.MISMATCH, "1:liveaaaaaaaa", "1:packbbbbbbbb", 0, List.of(e1, e2));
        List<String> lines = SeCommand.gateLines(report, Messages.defaults(), "cosmic", false);

        assertEquals(5, lines.size()); // mismatch + errors-count + 2 blocking lines + abort
        String body = String.join("\n", lines);
        assertTrue(body.contains("first boom") && body.contains("second boom"), body); // every blocking line rendered
        assertTrue(body.contains("1:packbbbbbbbb") && body.contains("1:liveaaaaaaaa"), body); // both short forms in the explanation
        assertTrue(lines.get(lines.size() - 1).contains("cosmic"), lines.get(lines.size() - 1)); // abort names the pack for the retry
    }

    @Test
    void gateUnstampedErrorsWithForceRendersUnstampedThenForced() {
        Diagnostic e = new Diagnostic(Severity.ERROR, "E_ONE", "boom", Source.of("enchants/a.yml", 3, 1), null);
        PackGate.Report report = new PackGate.Report(
                PackGate.FingerprintStatus.UNSTAMPED, "1:live", "", 0, List.of(e));
        List<String> lines = SeCommand.gateLines(report, Messages.defaults(), "cosmic", true);

        assertEquals(4, lines.size()); // unstamped + errors-count + 1 blocking line + forced
        assertTrue(String.join("\n", lines).contains("boom"), lines.toString());
        // The forced verdict (not abort) — abort is the only line that names the pack.
        assertTrue(!lines.get(lines.size() - 1).contains("cosmic"), lines.get(lines.size() - 1));
    }

    @Test
    void gateMatchWarningsProceedsWithACountLine() {
        Diagnostic w1 = new Diagnostic(Severity.WARNING, "W_ONE", "smell a", Source.UNKNOWN, null);
        Diagnostic w2 = new Diagnostic(Severity.WARNING, "W_TWO", "smell b", Source.UNKNOWN, null);
        Diagnostic w3 = new Diagnostic(Severity.WARNING, "W_THREE", "smell c", Source.UNKNOWN, null);
        PackGate.Report report = new PackGate.Report(
                PackGate.FingerprintStatus.MATCH, "1:live", "1:live", 0, List.of(w1, w2, w3));
        List<String> lines = SeCommand.gateLines(report, Messages.defaults(), "cosmic", false);
        assertEquals(1, lines.size()); // MATCH → no explanation; warnings-only → one count line
        assertTrue(lines.get(0).contains("3"), lines.get(0)); // the warning count
    }
}
