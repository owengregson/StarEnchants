package compile.cond;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.model.cond.Cond;
import compile.model.cond.NumExpr;
import compile.model.cond.StrExpr;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import schema.diag.DiagCode;
import schema.diag.Diagnostics;
import schema.diag.Source;
import schema.grammar.expr.Expr;
import schema.grammar.expr.ExprParser;

/**
 * The {@code %target.*%} SUBJECT scope (ADR-0076): which facts it admits, what each lowers to, and — the
 * point of the whole design — that reading it outside an effect row is a BLOCKING diagnostic rather than a
 * silent zero. That legality rule is what makes the {@code %victim%}-vs-{@code %target%} confusion
 * unauthorable instead of merely documented.
 */
class SubjectScopeTest {

    private static final Source SRC = Source.of("enchants/pummel.yml", 12, 5);

    /** The ability-level compiler: gates 7 and 8 run before any selector resolves, so there is no subject. */
    private static ConditionCompiler abilityLevel() {
        return new ConditionCompiler(VarResolver.none());
    }

    /** The effect-side compiler: an each-if / each-chance / expression argument, where a subject exists. */
    private static ConditionCompiler effectSide() {
        return new ConditionCompiler(VarResolver.none(), compile.resolve.PlatformResolvers.none(), true);
    }

    private static Expr parse(String raw, Diagnostics diags) {
        return ExprParser.parse(raw, SRC, diags).orElseThrow();
    }

    // ── the vocabulary ──

    @TestFactory
    Stream<DynamicTest> everyAdmittedNumericSubjectFactLowersToItsOwnNode() {
        record Row(String token, Class<? extends NumExpr> node) { }
        List<Row> rows = List.of(
                new Row("%target.enchlevel.metaphysical%", NumExpr.EnchantLevel.class),
                new Row("%target.crystals.ranger%", NumExpr.CrystalCount.class),
                new Row("%target.var.stacks%", NumExpr.EntityVar.class),
                new Row("%target.souls%", NumExpr.SubjectNum.class),
                new Row("%target.heroicpieces%", NumExpr.SubjectNum.class),
                new Row("%target.roll%", NumExpr.SubjectNum.class));
        return rows.stream().map(row -> DynamicTest.dynamicTest(row.token(), () -> {
            Diagnostics diags = new Diagnostics();
            NumExpr lowered = effectSide().numeric(parse(row.token(), diags), diags).orElseThrow();
            assertFalse(diags.hasErrors(), () -> diags.all().toString());
            assertInstanceOf(row.node(), lowered);
        }));
    }

    @Test
    void theKeyedFamiliesCarryTheTargetScopeAndTheLowerCasedKey() {
        // The scope is the whole point: a TARGET-scoped node evaluated against the victim's facts would be a
        // silent transposition, so the lowered node must name the subject explicitly.
        Diagnostics diags = new Diagnostics();
        NumExpr.EnchantLevel ench = assertInstanceOf(NumExpr.EnchantLevel.class,
                effectSide().numeric(parse("%target.enchlevel.Metaphysical%", diags), diags).orElseThrow());
        assertEquals(NumExpr.Scope.TARGET, ench.scope());
        assertEquals("metaphysical", ench.key());

        NumExpr.CrystalCount crystal = assertInstanceOf(NumExpr.CrystalCount.class,
                effectSide().numeric(parse("%target.crystals.Ranger%", diags), diags).orElseThrow());
        assertEquals(NumExpr.Scope.TARGET, crystal.scope());
        assertEquals("ranger", crystal.key());

        // A var name is handed over WHOLE, inner dots and all — the same rule %victim.var.<name>% follows.
        NumExpr.EntityVar var = assertInstanceOf(NumExpr.EntityVar.class,
                effectSide().numeric(parse("%target.var.mark.beast%", diags), diags).orElseThrow());
        assertEquals(NumExpr.Scope.TARGET, var.scope());
        assertEquals("mark.beast", var.name());
        assertFalse(diags.hasErrors(), () -> diags.all().toString());
    }

    @Test
    void theStringSubjectFactsCompareAsStrings() {
        Diagnostics diags = new Diagnostics();
        Cond typed = effectSide().compile(parse("%target.type% == \"PLAYER\"", diags), diags).orElseThrow();
        Cond.StrCmp cmp = assertInstanceOf(Cond.StrCmp.class, typed);
        assertEquals(StrExpr.SubjectText.TYPE,
                assertInstanceOf(StrExpr.SubjectStr.class, cmp.left()).fact());

        Cond related = effectSide().compile(parse("%target.relation% != \"ALLY\"", diags), diags).orElseThrow();
        assertEquals(StrExpr.SubjectText.RELATION, assertInstanceOf(StrExpr.SubjectStr.class,
                assertInstanceOf(Cond.StrCmp.class, related).left()).fact());
        assertFalse(diags.hasErrors(), () -> diags.all().toString());
    }

    // ── the legality rule ──

    @TestFactory
    Stream<DynamicTest> readingTheSubjectOutsideAnEffectRowIsBlocking() {
        // Both ability-level entry points: `condition:` (a boolean gate) and `chance:` (a numeric expression).
        return Stream.of(
                DynamicTest.dynamicTest("condition:", () -> {
                    Diagnostics diags = new Diagnostics();
                    assertTrue(abilityLevel().compile(
                            parse("%target.enchlevel.metaphysical% > 0", diags), diags).isEmpty());
                    assertBlocked(diags);
                }),
                DynamicTest.dynamicTest("chance:", () -> {
                    Diagnostics diags = new Diagnostics();
                    assertTrue(abilityLevel().numeric(
                            parse("6 - 4 * %target.enchlevel.metaphysical%", diags), diags).isEmpty());
                    assertBlocked(diags);
                }),
                DynamicTest.dynamicTest("bare boolean", () -> {
                    Diagnostics diags = new Diagnostics();
                    assertTrue(abilityLevel().compile(parse("%target.roll%", diags), diags).isEmpty());
                    assertBlocked(diags);
                }));
    }

    @TestFactory
    Stream<DynamicTest> everyLiveEntityReadIsRejectedByNameEvenOnTheEffectSide() {
        // The Folia rule made STRUCTURAL: the per-target pass decides about a body without touching it, so the
        // facts that would need an entity read do not exist in the scope at all — and naming one is loud.
        return Stream.of("%target.health%", "%target.maxhealth%", "%target.food%", "%target.sneaking%",
                        "%target.inzone%", "%target.potion.SLOW%", "%target.y%")
                .map(token -> DynamicTest.dynamicTest(token, () -> {
                    Diagnostics diags = new Diagnostics();
                    assertTrue(effectSide().compile(parse(token + " > 0", diags), diags).isEmpty());
                    assertBlocked(diags);
                }));
    }

    @Test
    void aStringSubjectFactInANumericPositionIsATypeErrorNotAScopeError() {
        // %target.type% exists — it is simply not a number. Reporting "no such fact" would send the author
        // hunting for a typo instead of telling them to compare it.
        Diagnostics diags = new Diagnostics();
        assertTrue(effectSide().numeric(parse("%target.type% * 2", diags), diags).isEmpty());
        assertTrue(diags.all().get(0).is(DiagCode.E_COND_TYPE), () -> diags.all().toString());
    }

    @Test
    void aForeignScopeIsStillAPlaceholderPassthrough() {
        // Only the exact `target` scope is claimed; `targets.` and a bare `%target%` must keep falling through
        // to PlaceholderAPI, or this wave would break an unrelated placeholder on somebody's server.
        Diagnostics diags = new Diagnostics();
        assertInstanceOf(NumExpr.Papi.class,
                abilityLevel().numeric(parse("%targets.count%", diags), diags).orElseThrow());
        assertInstanceOf(NumExpr.Papi.class,
                abilityLevel().numeric(parse("%target%", diags), diags).orElseThrow());
        assertFalse(diags.hasErrors(), () -> diags.all().toString());
    }

    private static void assertBlocked(Diagnostics diags) {
        assertTrue(diags.hasErrors(), "the subject scope must never fail open");
        assertTrue(diags.all().get(0).is(DiagCode.E_VAR_SCOPE), () -> diags.all().toString());
    }

    @Test
    void anUnknownEnchantOrCrystalKeyStillLowersCleanly() {
        // The victim-scope rule, kept: a pack may legitimately not define an enchant, and that reads 0 —
        // only the SCOPE vocabulary is closed, never the author-chosen key.
        Diagnostics diags = new Diagnostics();
        Optional<NumExpr> lowered = effectSide().numeric(parse("%target.enchlevel.not-a-real-enchant%", diags), diags);
        assertTrue(lowered.isPresent());
        assertFalse(diags.hasErrors(), () -> diags.all().toString());
    }
}
