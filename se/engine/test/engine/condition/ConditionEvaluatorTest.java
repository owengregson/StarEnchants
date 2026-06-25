package engine.condition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.model.CompiledCondition;
import compile.model.cond.Cond;
import compile.model.cond.NumExpr;
import compile.model.cond.StrExpr;
import schema.diag.Source;
import schema.grammar.expr.Cmp;
import schema.grammar.expr.FlowKind;
import org.junit.jupiter.api.Test;

class ConditionEvaluatorTest {

    private static boolean pass(Cond root, FactBuffer f) {
        return ConditionEvaluator.eval(CompiledCondition.gate(root, Source.UNKNOWN), f).passes();
    }

    @Test
    void nullConditionAlwaysContinues() {
        ConditionResult r = ConditionEvaluator.eval(null, new FactBuffer(0, 0, 0));
        assertEquals(Flow.CONTINUE, r.flow());
        assertTrue(r.passes());
    }

    @Test
    void bareGateContinuesOnPassAndStopsOnFail() {
        FactBuffer f = new FactBuffer(0, 1, 0);
        f.setFlag(0, true);
        assertEquals(Flow.CONTINUE, ConditionEvaluator.eval(
                CompiledCondition.gate(new Cond.BoolVar(0), Source.UNKNOWN), f).flow());
        f.setFlag(0, false);
        assertEquals(Flow.STOP, ConditionEvaluator.eval(
                CompiledCondition.gate(new Cond.BoolVar(0), Source.UNKNOWN), f).flow());
    }

    @Test
    void forceClauseForcesOnPassAndContinuesOnFail() {
        FactBuffer f = new FactBuffer(0, 1, 0);
        CompiledCondition force = new CompiledCondition(
                new Cond.BoolVar(0), FlowKind.FORCE, FlowKind.CONTINUE, 0.0, Source.UNKNOWN);
        f.setFlag(0, true);
        assertEquals(Flow.FORCE, ConditionEvaluator.eval(force, f).flow());
        f.setFlag(0, false);
        assertEquals(Flow.CONTINUE, ConditionEvaluator.eval(force, f).flow()); // a failing clause never STOPs, only its on-fail flow
    }

    @Test
    void allowClauseAllowsOnPass() {
        FactBuffer f = new FactBuffer(0, 1, 0);
        f.setFlag(0, true);
        CompiledCondition allow = new CompiledCondition(
                new Cond.BoolVar(0), FlowKind.ALLOW, FlowKind.CONTINUE, 0.0, Source.UNKNOWN);
        assertEquals(Flow.ALLOW, ConditionEvaluator.eval(allow, f).flow());
    }

    @Test
    void stopClauseStopsOnPassAndContinuesOnFail() {
        FactBuffer f = new FactBuffer(0, 1, 0);
        CompiledCondition stop = new CompiledCondition(
                new Cond.BoolVar(0), FlowKind.STOP, FlowKind.CONTINUE, 0.0, Source.UNKNOWN);
        f.setFlag(0, true);
        assertEquals(Flow.STOP, ConditionEvaluator.eval(stop, f).flow());
        f.setFlag(0, false);
        assertEquals(Flow.CONTINUE, ConditionEvaluator.eval(stop, f).flow());
    }

    @Test
    void chanceClauseAppliesDeltaOnlyWhenTestPasses() {
        FactBuffer f = new FactBuffer(0, 1, 0);
        CompiledCondition chance = new CompiledCondition(
                new Cond.BoolVar(0), FlowKind.CONTINUE, FlowKind.CONTINUE, 50.0, Source.UNKNOWN);
        f.setFlag(0, true);
        ConditionResult hit = ConditionEvaluator.eval(chance, f);
        assertEquals(Flow.CONTINUE, hit.flow());
        assertEquals(50.0, hit.chanceDelta());
        f.setFlag(0, false);
        ConditionResult miss = ConditionEvaluator.eval(chance, f);
        assertEquals(Flow.CONTINUE, miss.flow());
        assertEquals(0.0, miss.chanceDelta());
    }

    @Test
    void numericComparisonOverASlot() {
        Cond c = new Cond.NumCmp(new NumExpr.Var(0), Cmp.LT, new NumExpr.Lit(5.0));
        FactBuffer f = new FactBuffer(1, 0, 0);
        f.setNumber(0, 3.0);
        assertTrue(pass(c, f));
        f.setNumber(0, 7.0);
        assertFalse(pass(c, f));
        f.setNumber(0, 5.0); // strict <
        assertFalse(pass(c, f));
    }

    @Test
    void everyComparator() {
        FactBuffer f = new FactBuffer(1, 0, 0);
        f.setNumber(0, 5.0);
        assertTrue(pass(new Cond.NumCmp(new NumExpr.Var(0), Cmp.EQ, new NumExpr.Lit(5)), f));
        assertTrue(pass(new Cond.NumCmp(new NumExpr.Var(0), Cmp.NE, new NumExpr.Lit(4)), f));
        assertTrue(pass(new Cond.NumCmp(new NumExpr.Var(0), Cmp.GE, new NumExpr.Lit(5)), f));
        assertTrue(pass(new Cond.NumCmp(new NumExpr.Var(0), Cmp.LE, new NumExpr.Lit(5)), f));
        assertTrue(pass(new Cond.NumCmp(new NumExpr.Var(0), Cmp.GT, new NumExpr.Lit(4)), f));
        assertFalse(pass(new Cond.NumCmp(new NumExpr.Var(0), Cmp.GT, new NumExpr.Lit(5)), f));
    }

    @Test
    void logicalCombinatorsShortCircuit() {
        FactBuffer f = new FactBuffer(0, 2, 0);
        f.setFlag(0, true);
        f.setFlag(1, false);
        assertTrue(pass(new Cond.Or(new Cond.BoolVar(0), new Cond.BoolVar(1)), f));
        assertFalse(pass(new Cond.And(new Cond.BoolVar(0), new Cond.BoolVar(1)), f));
        assertTrue(pass(new Cond.Not(new Cond.BoolVar(1)), f));
        assertTrue(pass(new Cond.BoolLit(true), f));
    }

    @Test
    void booleanEqualityNode() {
        FactBuffer f = new FactBuffer(0, 1, 0);
        f.setFlag(0, true);
        assertTrue(pass(new Cond.BoolCmp(new Cond.BoolVar(0), true, new Cond.BoolLit(true)), f));
        assertFalse(pass(new Cond.BoolCmp(new Cond.BoolVar(0), false, new Cond.BoolLit(true)), f)); // != true
    }

    @Test
    void stringComparisonIsCaseInsensitive() {
        FactBuffer f = new FactBuffer(0, 0, 1);
        f.setString(0, "Nether");
        assertTrue(pass(new Cond.StrCmp(new StrExpr.Var(0), true, new StrExpr.Lit("nether")), f));
        assertFalse(pass(new Cond.StrCmp(new StrExpr.Var(0), true, new StrExpr.Lit("end")), f));
        assertTrue(pass(new Cond.StrCmp(new StrExpr.Var(0), false, new StrExpr.Lit("end")), f)); // !=
    }

    @Test
    void containsIsCaseInsensitiveSubstringWithPipeOr() {
        FactBuffer f = new FactBuffer(0, 0, 1);
        f.setString(0, "Diamond Sword");
        assertTrue(pass(new Cond.StrContains(new StrExpr.Var(0), new StrExpr.Lit("sword")), f));
        assertTrue(pass(new Cond.StrContains(new StrExpr.Var(0), new StrExpr.Lit("axe|sword")), f)); // pipe-OR, 2nd hits
        assertFalse(pass(new Cond.StrContains(new StrExpr.Var(0), new StrExpr.Lit("axe|bow")), f));
        f.setString(0, null);
        assertFalse(pass(new Cond.StrContains(new StrExpr.Var(0), new StrExpr.Lit("x")), f)); // null haystack → false
    }

    @Test
    void regexIsAFullMatch() {
        FactBuffer f = new FactBuffer(0, 0, 1);
        f.setString(0, "abc123");
        assertTrue(pass(new Cond.Regex(new StrExpr.Var(0), java.util.regex.Pattern.compile("[a-z]+[0-9]+")), f));
        // .matches() is anchored: a pattern matching only a prefix does not match the whole string.
        assertFalse(pass(new Cond.Regex(new StrExpr.Var(0), java.util.regex.Pattern.compile("[0-9]+")), f));
        f.setString(0, null);
        assertFalse(pass(new Cond.Regex(new StrExpr.Var(0), java.util.regex.Pattern.compile(".*")), f)); // null → false
    }

    @Test
    void papiNumericResolvedThroughTheBuffer() {
        Cond c = new Cond.NumCmp(new NumExpr.Papi("level"), Cmp.GT, new NumExpr.Lit(10));
        FactBuffer f = new FactBuffer(0, 0, 0);
        f.papiResolver(t -> "level".equals(t) ? "20" : null);
        assertTrue(pass(c, f));
        f.papiResolver(t -> "5");
        assertFalse(pass(c, f));
    }

    @Test
    void unresolvedPapiFailsNumericComparisonsClosedExceptNotEqual() {
        FactBuffer f = new FactBuffer(0, 0, 0); // default resolver returns null → NaN
        assertFalse(pass(new Cond.NumCmp(new NumExpr.Papi("x"), Cmp.GT, new NumExpr.Lit(0)), f));
        assertFalse(pass(new Cond.NumCmp(new NumExpr.Papi("x"), Cmp.EQ, new NumExpr.Lit(0)), f));
        assertTrue(pass(new Cond.NumCmp(new NumExpr.Papi("x"), Cmp.NE, new NumExpr.Lit(0)), f));
    }

    @Test
    void papiBooleanCoercionReadsTruthyValues() {
        Cond c = new Cond.BoolCmp(new Cond.BoolPapi("afk"), true, new Cond.BoolLit(true));
        FactBuffer f = new FactBuffer(0, 0, 0);
        f.papiResolver(t -> "true");
        assertTrue(pass(c, f));
        f.papiResolver(t -> "yes"); // truthy synonym
        assertTrue(pass(c, f));
        f.papiResolver(t -> "false");
        assertFalse(pass(c, f));
        f.papiResolver(t -> null); // absent placeholder → false (fail-closed)
        assertFalse(pass(c, f));
    }

    @Test
    void papiStringComparison() {
        Cond c = new Cond.StrCmp(new StrExpr.Papi("world"), true, new StrExpr.Lit("nether"));
        FactBuffer f = new FactBuffer(0, 0, 0);
        f.papiResolver(t -> "NETHER");
        assertTrue(pass(c, f));
    }
}
