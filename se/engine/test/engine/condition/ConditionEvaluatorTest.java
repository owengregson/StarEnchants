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
import org.junit.jupiter.api.Test;

class ConditionEvaluatorTest {

    private static boolean pass(Cond root, FactBuffer f) {
        return ConditionEvaluator.eval(new CompiledCondition(root, Source.UNKNOWN), f).passes();
    }

    @Test
    void nullConditionAlwaysContinues() {
        ConditionResult r = ConditionEvaluator.eval(null, new FactBuffer(0, 0, 0));
        assertEquals(Flow.CONTINUE, r.flow());
        assertTrue(r.passes());
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
        assertTrue(pass(c, f)); // case-insensitive
    }
}
