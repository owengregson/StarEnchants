package bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.model.Interner;
import compile.model.Interners;
import compile.model.ScopeKinds;
import compile.model.Snapshot;
import compile.model.SourceKind;
import compile.model.SourceMap;
import engine.pipeline.GateOutcome;
import engine.stores.WhyRing;
import item.worn.WornState;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import platform.lang.Messages;
import schema.diag.Source;
import testfx.Snapshots;
import testfx.WornStates;

/**
 * Pure rendering of {@code /se why} (ADR-0045): each row's routing + decoded payload, the gen-mismatch and
 * out-of-range guards, the filter grammar, depth caps, and the empty-filter diagnosis notes. Expected lines
 * are built from the SAME {@link Messages} catalogue production reads (never a re-typed lang string): the test
 * pins only the values WhyRender computes and which lang key each verdict routes to.
 */
class WhyRenderTest {

    private static final Messages M = Messages.defaults();
    private static final int GEN = 5;
    private static final int VENOM = 10; // defId -> enchants/venom/3
    private static final int YETI = 20;  // defId -> sets/yeti  (the suppressor source)

    private static Snapshot snapshot() {
        return snapshot("enchants/venom/3"); // stableKeys only matter to the filter-diagnosis notes
    }

    private static Snapshot snapshot(String... stableKeysInDenseOrder) {
        Interner worlds = new Interner();
        worlds.intern("nether"); // world id 0
        Interner triggers = new Interner();
        triggers.intern("MINE");   // trigger id 0
        triggers.intern("ATTACK"); // trigger id 1
        Interner suppress = new Interner();
        suppress.intern("fireball"); // suppress id 0
        Interner scopes = new Interner();
        scopes.intern("defense");   // cooldown-scope id 0
        scopes.intern("lifesteal"); // cooldown-scope id 1
        SourceMap sourceMap = new SourceMap(Map.of(
                VENOM, new SourceMap.Entry(SourceKind.ENCHANT, "enchants/venom/3", Source.UNKNOWN),
                YETI, new SourceMap.Entry(SourceKind.SET, "sets/yeti", Source.UNKNOWN)));
        return Snapshots.snapshot().generation(GEN)
                .interners(new Interners(worlds, triggers, suppress, scopes))
                .sourceMap(sourceMap).stableKeys(stableKeysInDenseOrder).build();
    }

    /** One venom (defId 10) attempt on trigger MINE at gen 5, tick 40 (nowTicks 100 -> "3s ago"). */
    private static WhyRing.Attempt at(GateOutcome verdict, int pA, int pB) {
        return new WhyRing.Attempt(0L, 40L, VENOM, verdict.ordinal(), 0, GEN, pA, pB);
    }

    /** The single rendered row for {@code a} (index 0 is the header). */
    private static String row(WhyRing.Attempt a) {
        return WhyRender.lines(List.of(a), snapshot(), 100L, null, List.of(), null, "Steve", M).get(1);
    }

    private static String attemptLine(String resultFragment) {
        return M.format("command.why.attempt", "AGO", 3L, "KEY", "[enchant] enchants/venom/3",
                "TRIGGER", "MINE", "RESULT", resultFragment);
    }

    @TestFactory
    Stream<DynamicTest> eachVerdictRoutesToItsKeyAndDecodesItsPayload() {
        return Stream.of(
                dyn(at(GateOutcome.BLOCKED_WORLD, 0, 0),
                        M.fragment("command.why.blocked-world", "WORLD", "nether")),
                dyn(at(GateOutcome.BLOCKED_PROTECTION, 0, 0),
                        M.fragment("command.why.blocked-protection")),
                dyn(at(GateOutcome.WRONG_TRIGGER, 0, 0),
                        M.fragment("command.why.wrong-trigger")),
                dyn(at(GateOutcome.OUT_OF_LEVEL, 7, 0),
                        M.fragment("command.why.out-of-level", "LEVEL", 7)),
                dyn(at(GateOutcome.SUPPRESSED, WhyRing.packScope(0, 0, 0), -1),
                        M.fragment("command.why.suppressed-transient", "KEY", "fireball")),
                dyn(at(GateOutcome.SUPPRESSED, WhyRing.packScope(1, ScopeKinds.GROUP, 0), YETI),
                        M.fragment("command.why.suppressed", "SCOPE", "GROUP", "KEY", "defense",
                                "BY", M.fragment("command.why.suppressed-by", "SOURCE", "sets/yeti"))),
                dyn(at(GateOutcome.ON_COOLDOWN, WhyRing.packScope(0, ScopeKinds.ENCHANT, 1), 32),
                        M.fragment("command.why.on-cooldown", "TICKS", 32, "SCOPE", "ENCHANT", "KEY", "lifesteal")),
                dyn(at(GateOutcome.CONDITION_FAILED, 0, 0),
                        M.fragment("command.why.condition")),
                dyn(at(GateOutcome.CHANCE_FAILED, 8720, 2500),
                        M.fragment("command.why.chance", "ROLL", "87.20", "CHANCE", "25.00")),
                // ADR-0076: the same payload pair, read the other way — the roll is UNDER the unrebated chance,
                // which is exactly what makes it a proc a rebate ate rather than an ordinary miss.
                dyn(at(GateOutcome.REBATED, 1900, 2500),
                        M.fragment("command.why.rebated", "ROLL", "19.00", "CHANCE", "25.00")),
                dyn(at(GateOutcome.CANCELLED, 0, 0),
                        M.fragment("command.why.cancelled")),
                dyn(at(GateOutcome.NO_SOULS, 0, 5),
                        M.fragment("command.why.no-souls-gem", "COST", 5)),
                dyn(at(GateOutcome.NO_SOULS, 1, 5),
                        M.fragment("command.why.no-souls-pool", "COST", 5)),
                dyn(at(GateOutcome.ACTIVATED, 1234, 2500),
                        M.fragment("command.why.fired", "ROLL", "12.34", "CHANCE", "25.00")),
                dyn(at(GateOutcome.ACTIVATED, -1, 2500),
                        M.fragment("command.why.fired-forced")));
    }

    private static DynamicTest dyn(WhyRing.Attempt a, String expectedResult) {
        return DynamicTest.dynamicTest(GateOutcome.values()[a.verdict()] + " pA=" + a.pA(),
                () -> assertEquals(attemptLine(expectedResult), row(a)));
    }

    @Test
    void aSuppressedRowWithNoAttributedSourceOmitsTheFromClause() {
        String result = M.fragment("command.why.suppressed", "SCOPE", "GROUP", "KEY", "defense", "BY", "");
        assertEquals(attemptLine(result),
                row(at(GateOutcome.SUPPRESSED, WhyRing.packScope(1, ScopeKinds.GROUP, 0), -1)));
    }

    @Test
    void theCrossRegionEvictionRaceRendersUnattributedNotAttributedToDefIdZero() {
        // gate 5's benign d==0 race (blockedDetail found no live window) packs ENCHANT/scope 0, unattributed
        // (pB -1); the row names the scope but drops the "from" clause — never attributing it to defId 0's key.
        String result = M.fragment("command.why.suppressed", "SCOPE", "ENCHANT", "KEY", "defense", "BY", "");
        assertEquals(attemptLine(result),
                row(at(GateOutcome.SUPPRESSED, WhyRing.packScope(1, ScopeKinds.ENCHANT, 0), -1)));
    }

    @Test
    void aGenMismatchRowRendersStaleWithTheRawDefId() {
        WhyRing.Attempt old = new WhyRing.Attempt(0L, 40L, VENOM, GateOutcome.ACTIVATED.ordinal(), 0, GEN - 1, 0, 0);
        assertEquals(M.format("command.why.stale", "AGO", 3L, "DEFID", VENOM), row(old));
    }

    @Test
    void anOutOfRangeVerdictRowIsSkipped() {
        // A future GateOutcome an old ring predates: guard against ArrayIndexOutOfBounds, drop the row.
        WhyRing.Attempt future = new WhyRing.Attempt(0L, 40L, VENOM, GateOutcome.values().length, 0, GEN, 0, 0);
        List<String> lines = WhyRender.lines(List.of(future), snapshot(), 100L, null, List.of(), null, "Steve", M);
        assertEquals(2, lines.size()); // header + "none", no row
        assertEquals(M.format("command.why.none", "PLAYER", "Steve"), lines.get(1));
    }

    @Test
    void agoClampsANegativeElapsedToZero() {
        // A racing writer can stamp a tick newer than the command's read.
        WhyRing.Attempt future = new WhyRing.Attempt(0L, 200L, VENOM, GateOutcome.CANCELLED.ordinal(), 0, GEN, 0, 0);
        String line = WhyRender.lines(List.of(future), snapshot(), 100L, null, List.of(), null, "Steve", M).get(1);
        assertTrue(line.contains("0s ago"), () -> line);
    }

    // ── filter grammar ─────────────────────────────────────────────────────────────────────────────────

    @Test
    void matchesAcceptsBarePrefixedAndPerAbilityKeysButNotAPrefixOfASegment() {
        assertTrue(WhyRender.matches("enchants/venom/3", "venom"));
        assertTrue(WhyRender.matches("enchants/venom/3", "enchants/venom"));
        assertTrue(WhyRender.matches("enchants/venom/3", "enchants/venom/3"));
        assertTrue(WhyRender.matches("sets/yeti", "yeti"));
        assertTrue(WhyRender.matches("sets/yeti/a2", "yeti"));
        assertTrue(WhyRender.matches("crystals/frost", "crystals/frost"));
        assertFalse(WhyRender.matches("enchants/venom/3", "ven"));  // a substring is not a match
        assertFalse(WhyRender.matches("enchants/venom/3", "yeti"));
    }

    // ── depth caps ─────────────────────────────────────────────────────────────────────────────────────

    @Test
    void unfilteredCapsAtTenRows() {
        List<WhyRing.Attempt> twelve = repeat(at(GateOutcome.ACTIVATED, 1234, 2500), 12);
        List<String> lines = WhyRender.lines(twelve, snapshot(), 100L, null, List.of(), null, "Steve", M);
        assertEquals(1 + WhyRender.DEFAULT_ROWS, lines.size()); // header + 10 rows
    }

    @Test
    void filteredCapsAtTwentyRows() {
        List<WhyRing.Attempt> twentyFive = repeat(at(GateOutcome.ACTIVATED, 1234, 2500), 25);
        List<String> lines = WhyRender.lines(twentyFive, snapshot(), 100L, "venom", List.of(), null, "Steve", M);
        assertEquals(1 + WhyRender.FILTERED_ROWS, lines.size()); // header + 20 matching rows, no notes
    }

    // ── notes ────────────────────────────────────────────────────────────────────────────────────────

    @Test
    void aFilterThatMatchesAQuarantinedKeyAppendsTheQuarantineNote() {
        List<String> lines = WhyRender.lines(List.of(at(GateOutcome.ACTIVATED, 1234, 2500)), snapshot(), 100L,
                "venom", List.of("enchants/venom"), null, "Steve", M);
        assertTrue(lines.contains(M.format("command.why.quarantined-note", "KEY", "enchants/venom")), () -> lines.toString());
    }

    @Test
    void anEmptyFilterMatchingWornGearNotesTheTriggersItListensOn() {
        Snapshot snap = snapshot("enchants/venom/3"); // dense id 0
        WornState worn = WornStates.worn().byTrigger(0, 0).build(); // MINE (trigger 0) fires dense id 0
        List<String> lines = WhyRender.lines(List.of(), snap, 100L, "venom", List.of(), worn, "Steve", M);
        assertTrue(lines.contains(M.format("command.why.worn-note-listening",
                "KEY", "venom", "PLAYER", "Steve", "TRIGGERS", "MINE")), () -> lines.toString());
    }

    @Test
    void anEmptyFilterForAKnownKeyNotOnWornGearNotesItIsMissing() {
        Snapshot snap = snapshot("enchants/venom/3");
        WornState worn = WornStates.worn().build(); // nothing worn
        List<String> lines = WhyRender.lines(List.of(), snap, 100L, "venom", List.of(), worn, "Steve", M);
        assertTrue(lines.contains(M.format("command.why.worn-note-missing", "KEY", "venom", "PLAYER", "Steve")),
                () -> lines.toString());
    }

    @Test
    void anEmptyFilterForAnUnknownKeyNotesNoContentMatches() {
        List<String> lines = WhyRender.lines(List.of(), snapshot("enchants/venom/3"), 100L, "ghost", List.of(),
                WornStates.worn().build(), "Steve", M);
        assertTrue(lines.contains(M.format("command.why.unknown-key", "KEY", "ghost")), () -> lines.toString());
    }

    private static List<WhyRing.Attempt> repeat(WhyRing.Attempt a, int n) {
        return Stream.generate(() -> a).limit(n).toList();
    }
}
