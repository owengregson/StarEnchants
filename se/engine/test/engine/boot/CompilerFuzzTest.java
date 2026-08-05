package engine.boot;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import compile.Compiler;
import compile.def.AbilityDef;
import compile.model.Ability;
import compile.model.Affinity;
import compile.model.CompiledEffect;
import compile.model.CompiledSelector;
import compile.model.ScopeKinds;
import compile.model.Snapshot;
import compile.model.cond.NumExpr;
import engine.effect.EffectKind;
import engine.effect.EffectRegistry;
import engine.effect.kind.BuiltinEffects;
import engine.selector.SelectorKind;
import engine.selector.SelectorRegistry;
import engine.selector.kind.BuiltinSelectors;
import engine.trigger.BuiltinTriggers;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import schema.diag.DiagCode;
import schema.diag.Diagnostic;
import schema.diag.Diagnostics;
import schema.spec.Args;
import schema.spec.Param;
import schema.spec.ParamSpec;
import schema.spec.ParamType;
import testfx.PermissiveResolvers;

/**
 * Registry-derived fuzz of the production content compiler: every registered effect/selector kind gets
 * seeded valid + near-valid content whose grammar is the kind's own {@link ParamSpec}, asserting totality
 * (never throws; every fault a closed-set {@link DiagCode}) and erase-stage soundness (ADR-0039, ADR-0042).
 * A new kind is fuzzed automatically; a kind that gains a {@code CrossRule} fails its valid family — teach
 * {@code ContentFuzz.validNamed} the rule, do not loosen the assertion.
 */
class CompilerFuzzTest {

    private static final Compiler COMPILER = ContentCompiler.production(PermissiveResolvers.INSTANCE);
    private static final ContentFuzz.Vocab VOCAB = ContentFuzz.Vocab.live();
    private static final EffectRegistry EFFECTS = BuiltinEffects.registry();
    private static final SelectorRegistry SELECTORS = BuiltinSelectors.registry();
    private static final List<String> TRIGGERS = BuiltinTriggers.registry().names();

    static final int VALID_CASES = 12;
    static final int ADVERSARIAL_CASES = 12;
    static final int SELECTOR_VALID_CASES = 6;
    static final int SELECTOR_ADVERSARIAL_CASES = 6;

    @Test
    void registriesAreNonEmpty() {
        assertFalse(EFFECTS.kinds().isEmpty());
        assertFalse(SELECTORS.kinds().isEmpty());
        assertFalse(TRIGGERS.isEmpty());
    }

    @TestFactory
    Stream<DynamicTest> everyEffectKindSurvivesFuzz() {
        return EFFECTS.kinds().stream().map(kind -> dynamicTest(kind.head(), () -> {
            List<ContentFuzz.Case> cases = new ArrayList<>();
            for (int i = 0; i < VALID_CASES; i++) {
                cases.add(ContentFuzz.valid(kind, i, VOCAB));
            }
            List<AbilityDef> defs = cases.stream().map(ContentFuzz.Case::def).toList();
            Diagnostics diags = new Diagnostics();
            Snapshot snapshot = assertDoesNotThrow(() -> COMPILER.compile(defs, 1, diags));
            assertFalse(diags.hasErrors(), validSupplier(kind, cases, diags));
            assertClosedCodes(diags);
            assertStructurallySound(snapshot);
            assertReflectsAuthored(snapshot, cases);

            for (int i = 0; i < ADVERSARIAL_CASES; i++) {
                ContentFuzz.Case c = ContentFuzz.adversarial(kind, i, VOCAB);
                Diagnostics d = new Diagnostics();
                Snapshot s = assertDoesNotThrow(() -> COMPILER.compile(List.of(c.def()), 1, d));
                assertClosedCodes(d);
                assertStructurallySound(s);
                if (c.expected() != null) {
                    assertTrue(d.all().stream().anyMatch(x -> x.is(c.expected())), caseSupplier(kind.head(), i, c, d));
                }
            }
        }));
    }

    @TestFactory
    Stream<DynamicTest> everySelectorKindSurvivesFuzz() {
        return SELECTORS.kinds().stream().map(sel -> dynamicTest(sel.head(), () -> {
            for (int i = 0; i < SELECTOR_VALID_CASES; i++) {
                ContentFuzz.Case c = ContentFuzz.selectorCase(sel, i, true, VOCAB);
                Diagnostics d = new Diagnostics();
                Snapshot s = assertDoesNotThrow(() -> COMPILER.compile(List.of(c.def()), 1, d));
                assertFalse(d.hasErrors(), caseSupplier(sel.head(), i, c, d));
                assertClosedCodes(d);
                assertStructurallySound(s);
            }
            for (int i = 0; i < SELECTOR_ADVERSARIAL_CASES; i++) {
                ContentFuzz.Case c = ContentFuzz.selectorCase(sel, i, false, VOCAB);
                Diagnostics d = new Diagnostics();
                Snapshot s = assertDoesNotThrow(() -> COMPILER.compile(List.of(c.def()), 1, d));
                assertClosedCodes(d);
                assertStructurallySound(s);
                if (c.expected() != null) {
                    assertTrue(d.all().stream().anyMatch(x -> x.is(c.expected())), caseSupplier(sel.head(), i, c, d));
                }
            }
        }));
    }

    @Test
    void crossKindSoupIsTotalAndDeterministic() {
        Diagnostics d1 = new Diagnostics();
        Diagnostics d2 = new Diagnostics();
        List<AbilityDef> defs1 = buildSoup();
        List<AbilityDef> defs2 = buildSoup(); // regenerated independently — pins generator determinism too
        Snapshot s1 = assertDoesNotThrow(() -> COMPILER.compile(defs1, 1, d1));
        Snapshot s2 = assertDoesNotThrow(() -> COMPILER.compile(defs2, 1, d2));

        for (Diagnostics d : List.of(d1, d2)) {
            assertClosedCodes(d);
            assertTrue(d.all().stream().anyMatch(x -> x.is(DiagCode.E_DUP_KEY)), () -> render(d));
        }
        assertStructurallySound(s1);
        assertStructurallySound(s2);

        assertEquals(s1.abilityCount(), s2.abilityCount());
        for (int i = 0; i < s1.abilityCount(); i++) {
            assertEquals(s1.stableKeys().keyOf(i), s2.stableKeys().keyOf(i));
            assertEquals(headsOf(s1.abilities()[i]), headsOf(s2.abilities()[i]));
        }
        assertEquals(s1.interners().triggers().names(), s2.interners().triggers().names());
        assertEquals(s1.interners().suppress().names(), s2.interners().suppress().names());
        assertEquals(s1.interners().cooldownScopes().names(), s2.interners().cooldownScopes().names());
        assertEquals(s1.interners().worlds().names(), s2.interners().worlds().names());
    }

    private static List<AbilityDef> buildSoup() {
        List<AbilityDef> defs = new ArrayList<>();
        List<EffectKind> kinds = VOCAB.effectKinds();
        for (int n = 0; n < 200; n++) {
            if (n % 10 == 9 && !defs.isEmpty()) {
                defs.add(defs.get(n - 1)); // reuse an earlier stable key → forces E_DUP_KEY
            } else {
                defs.add(ContentFuzz.valid(kinds.get(n % kinds.size()), n, VOCAB).def());
            }
        }
        return defs;
    }

    private static List<String> headsOf(Ability a) {
        List<String> heads = new ArrayList<>();
        for (CompiledEffect e : a.effects()) {
            heads.add(e.head());
        }
        return heads;
    }

    // ─────────────────────────────────────────────────── shared assertion helpers ──

    /** ADR-0042 closed-set totality: every emitted code is a real {@link DiagCode} constant. */
    static void assertClosedCodes(Diagnostics diags) {
        assertClosedCodes(diags.all());
    }

    static void assertClosedCodes(List<Diagnostic> diags) {
        for (Diagnostic d : diags) {
            assertDoesNotThrow(() -> DiagCode.valueOf(d.code()), () -> "off-catalogue diagnostic code: " + d.code());
        }
    }

    /** The erase/lower invariants that hold on EVERY returned snapshot, even error-laden ones. */
    static void assertStructurallySound(Snapshot s) {
        int suppressSize = s.interners().suppress().size();
        int scopeSize = s.interners().cooldownScopes().size();
        int triggerLimit = Math.min(32, s.interners().triggers().size());
        long triggerAllowed = triggerLimit >= 32 ? 0xFFFFFFFFL : (1L << triggerLimit) - 1;

        for (int i = 0; i < s.abilityCount(); i++) {
            Ability a = s.abilities()[i];
            assertEquals(i, a.id());                                        // dense id = array position
            assertEquals(i, s.stableKeys().idOf(s.stableKeys().keyOf(i)));

            Affinity fold = Affinity.CONTEXT_LOCAL;
            int prevWait = 0;
            for (CompiledEffect e : a.effects()) {
                assertTrue(EFFECTS.lookup(e.head()).isPresent(), () -> "unknown head survived: " + e.head());
                assertEquals(EFFECTS.idOf(e.head()), e.kindId());
                assertTrue(e.kindId() >= 0);
                assertSame(EFFECTS.lookup(e.head()).get().spec().affinity(), e.affinity());
                assertFalse(e.head().equals("WAIT"));                        // WAIT is timing, never emitted
                assertTrue(e.cumulativeWaitTicks() >= prevWait);
                prevWait = e.cumulativeWaitTicks();
                fold = fold.max(e.affinity());
                assertSelectorSound(e.target());
                assertHandlesInterned(e);
                assertSuppressBridge(e, scopeSize);
            }
            assertSame(fold, a.affinity());                                 // ability affinity = MAX fold

            assertEquals(0L, Integer.toUnsignedLong(a.triggerMask()) & ~triggerAllowed);
            assertScope(a.suppressKey(), suppressSize);
            assertScope(a.cdScopeEnchant(), scopeSize);
            assertScope(a.cdScopeGroup(), scopeSize);
            assertScope(a.cdScopeType(), scopeSize);
        }
    }

    private static void assertSelectorSound(CompiledSelector sel) {
        boolean self = sel.kindId() == -1 && sel.head().equals(CompiledSelector.SELF.head());
        assertTrue(self || sel.kindId() == SELECTORS.idOf(sel.head()),
                () -> "selector kindId mismatch: head=" + sel.head() + " id=" + sel.kindId());
    }

    /** §9: no HANDLE-typed arg survives to runtime as a String (resolve interned it or dropped the effect). */
    private static void assertHandlesInterned(CompiledEffect e) {
        ParamSpec spec = EFFECTS.lookup(e.head()).get().spec().paramSpec();
        for (Param p : spec.params()) {
            if (p.type().kind() == ParamType.Kind.HANDLE && e.args().has(p.name())) {
                assertFalse(e.args().opt(p.name()).orElseThrow() instanceof String,
                        () -> "handle '" + p.name() + "' of '" + e.head() + "' survived as a String");
            }
        }
    }

    /** The erase SUPPRESS bridge: scope/key rewritten into gate 5's shared cooldown-scope namespace
     *  (scope KIND: the key is a dense effect kindId instead, ADR-0053); mode lowered to its wire ordinal. */
    private static void assertSuppressBridge(CompiledEffect e, int scopeSize) {
        Args args = e.args();
        if (!isSuppressLike(e.head()) || !args.has("scope") || !args.has("key")) {
            return;
        }
        Object scope = args.opt("scope").orElseThrow();
        Object key = args.opt("key").orElseThrow();
        assertTrue(scope instanceof Long, () -> "SUPPRESS scope not erased to long: " + scope);
        long sk = (Long) scope;
        assertTrue(sk == ScopeKinds.ENCHANT || sk == ScopeKinds.GROUP || sk == ScopeKinds.TYPE
                || sk == ScopeKinds.KIND);
        assertTrue(key instanceof Long, () -> "SUPPRESS key not erased to long: " + key);
        if (sk == ScopeKinds.KIND) {
            assertTrue((Long) key >= 0 && (Long) key < EFFECTS.kinds().size(),
                    () -> "SUPPRESS KIND key is not a dense effect kindId: " + key);
        } else {
            assertTrue((Long) key < scopeSize);
        }
        if (args.has("mode")) {
            Object mode = args.opt("mode").orElseThrow();
            assertTrue(mode instanceof Long m && (m == 0L || m == 1L),
                    () -> "SUPPRESS mode not erased to its wire ordinal: " + mode);
        }
    }

    /** The two heads the erase stage lowers through the one scope/key bridge (outgoing and incoming). */
    private static boolean isSuppressLike(String head) {
        return head.equals("SUPPRESS") || head.equals("SUPPRESS_INCOMING");
    }

    private static void assertScope(int id, int size) {
        assertTrue(id == -1 || (id >= 0 && id < size), () -> "interned id out of range: " + id + " / " + size);
    }

    /** Clean-compile round-trip: every authored value survives untouched (or as its documented lowered/erased form). */
    static void assertReflectsAuthored(Snapshot s, List<ContentFuzz.Case> cases) {
        for (ContentFuzz.Case c : cases) {
            AbilityDef def = c.def();
            Ability a = s.byStableKey(def.stableKey());
            if (a == null) {
                continue; // dup-dropped
            }
            assertEquals(def.level(), a.level());
            assertEquals(def.cooldownTicks(), a.cooldownTicks());
            assertEquals(def.soulCost(), a.soulCost());
            assertEquals(def.repeatTicks(), a.repeatTicks());
            assertEquals(def.setPieces(), a.setPieces());
            assertTrue(def.baseChance() == a.baseChance()); // same double travels untouched through lowering

            for (String t : def.triggers()) {
                int id = s.interners().triggers().idOf(t.toUpperCase(Locale.ROOT));
                assertTrue(id >= 0);
                assertTrue(a.firesOn(id));
                assertEquals(TRIGGERS.indexOf(t.toUpperCase(Locale.ROOT)), id); // §3.7 vocabulary agreement
            }
            for (String w : def.worldBlacklist()) {
                assertTrue(a.blockedInWorld(s.interners().worlds().idOf(w)));
            }

            assertEffects(c, a);

            if (def.suppressKey() != null) {
                assertEquals(def.suppressKey(), s.interners().suppress().nameOf(a.suppressKey()));
            }
            assertScopeName(def.cdScopeEnchant(), a.cdScopeEnchant(), s);
            assertScopeName(def.cdScopeGroup(), a.cdScopeGroup(), s);
            // TYPE alone case-folds at erase (R-QC3): its vocabulary is the combat direction the compiler
            // stamps, so `key: defense` and `key: DEFENSE` must reach one scope. ENCHANT/GROUP keep their
            // authored spelling, since they key stable keys and authored group names.
            assertScopeName(def.cdScopeType() == null ? null : def.cdScopeType().toUpperCase(Locale.ROOT),
                    a.cdScopeType(), s);
        }
    }

    private static void assertScopeName(String authored, int id, Snapshot s) {
        if (authored != null) {
            assertEquals(authored, s.interners().cooldownScopes().nameOf(id));
        }
    }

    private static void assertEffects(ContentFuzz.Case c, Ability a) {
        List<ContentFuzz.GeneratedLine> nonWait = new ArrayList<>();
        List<Integer> cumWaits = new ArrayList<>();
        int wait = 0;
        for (ContentFuzz.GeneratedLine line : c.lines()) {
            if (line.head().equals("WAIT")) {
                wait += Integer.parseInt(line.named().get("ticks").raw());
                continue;
            }
            nonWait.add(line);
            cumWaits.add(wait);
        }
        assertEquals(nonWait.size(), a.effects().length);
        for (int j = 0; j < nonWait.size(); j++) {
            ContentFuzz.GeneratedLine line = nonWait.get(j);
            CompiledEffect e = a.effects()[j];
            assertEquals(line.head(), e.head());
            assertEquals((int) cumWaits.get(j), e.cumulativeWaitTicks()); // running WAIT sum before this line
            assertParams(line, e);
        }
    }

    private static void assertParams(ContentFuzz.GeneratedLine line, CompiledEffect e) {
        Args args = e.args();
        ParamSpec spec = EFFECTS.lookup(e.head()).get().spec().paramSpec();
        boolean suppress = isSuppressLike(e.head());
        for (Param p : spec.params()) {
            String name = p.name();
            if (suppress && (name.equals("scope") || name.equals("key") || name.equals("mode"))) {
                continue; // erase-rewritten to longs (asserted by the SUPPRESS bridge)
            }
            ContentFuzz.Authored auth = line.named().get(name);
            if (auth == null) {
                if (p.type().defaultRaw().isPresent()) {
                    assertTrue(args.has(name), () -> "default not filled: " + name);
                } else {
                    assertFalse(args.has(name), () -> "absent optional present: " + name);
                }
                continue;
            }
            switch (auth.kind()) {
                case NUM_LITERAL -> assertTrue(args.dbl(name) == Double.parseDouble(auth.raw()),
                        () -> name + " = " + args.opt(name) + " != " + auth.raw());
                case NUM_EXPR -> assertTrue(args.opt(name).orElseThrow() instanceof NumExpr);
                case BOOL -> assertEquals(isTruthy(auth.raw()), args.bool(name));
                case ENUM -> assertEquals(canonicalEnum(p.type(), auth.raw()), args.str(name));
                case STRING -> assertEquals(auth.raw(), args.str(name));
                // A single handle resolves to one interned id; a handle LIST to the ids of its entries.
                case HANDLE -> assertTrue(p.type().isList()
                        ? args.opt(name).orElseThrow() instanceof List
                        : args.opt(name).orElseThrow() instanceof Integer);
            }
        }
    }

    /** Truthiness of an authored boolean token — mirrors {@code ParamType.parseBool}'s truthy vocabulary. */
    private static boolean isTruthy(String token) {
        String t = token.trim().toLowerCase(Locale.ROOT);
        return t.equals("true") || t.equals("yes") || t.equals("on") || t.equals("1");
    }

    private static String canonicalEnum(ParamType type, String raw) {
        for (String allowed : type.allowed()) {
            if (allowed.equalsIgnoreCase(raw)) {
                return allowed;
            }
        }
        throw new AssertionError("no canonical spelling of '" + raw + "' in " + type.allowed());
    }

    // ─────────────────────────────────────────────────────── failure rendering ──

    private static Supplier<String> validSupplier(EffectKind kind, List<ContentFuzz.Case> cases, Diagnostics diags) {
        return () -> {
            StringBuilder sb = new StringBuilder("head=" + kind.head() + " VALID batch\n");
            for (int i = 0; i < cases.size(); i++) {
                sb.append("case=").append(i).append(" seed=").append(ContentFuzz.seedFor(kind.head(), i)).append('\n');
                sb.append(ContentFuzz.describe(cases.get(i).def()));
            }
            return sb.append(render(diags)).toString();
        };
    }

    private static Supplier<String> caseSupplier(String head, int i, ContentFuzz.Case c, Diagnostics d) {
        return () -> "head=" + head + " case=" + i + " (" + c.label() + ") seed=" + ContentFuzz.seedFor(head, i)
                + "\n" + ContentFuzz.describe(c.def()) + render(d);
    }

    private static String render(Diagnostics diags) {
        StringBuilder sb = new StringBuilder();
        for (Diagnostic d : diags.all()) {
            sb.append(d.render()).append('\n');
        }
        return sb.toString();
    }
}
