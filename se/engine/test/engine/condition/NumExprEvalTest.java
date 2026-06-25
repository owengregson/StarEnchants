package engine.condition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.model.cond.NumExpr;
import org.junit.jupiter.api.Test;

/**
 * {@link NumExprEval} is shared by conditions and expression-valued effect args, so its
 * divide-by-zero rule must fail safe (0, never NaN) lest a poisoned value reach the fold
 * (docs/architecture.md §3.4).
 */
class NumExprEvalTest {

    @Test
    void evaluatesArithmeticOverFactSlots() {
        FactBuffer facts = new FactBuffer(2, 0, 0);
        facts.setNumber(0, 5.0);
        facts.setNumber(1, 3.0);
        // (%combo% * 10) + 2  ->  52
        NumExpr expr = new NumExpr.Bin(
                new NumExpr.Bin(new NumExpr.Var(0), NumExpr.Op.MULTIPLY, new NumExpr.Lit(10)),
                NumExpr.Op.ADD, new NumExpr.Lit(2));
        assertEquals(52.0, NumExprEval.eval(expr, facts));
        // 25 - (%distance% * 7)  ->  4
        NumExpr falloff = new NumExpr.Bin(new NumExpr.Lit(25), NumExpr.Op.SUBTRACT,
                new NumExpr.Bin(new NumExpr.Var(1), NumExpr.Op.MULTIPLY, new NumExpr.Lit(7)));
        assertEquals(4.0, NumExprEval.eval(falloff, facts));
    }

    @Test
    void negationAndDivision() {
        FactBuffer facts = new FactBuffer(1, 0, 0);
        facts.setNumber(0, 20.0);
        assertEquals(-20.0, NumExprEval.eval(new NumExpr.Neg(new NumExpr.Var(0)), facts));
        assertEquals(10.0, NumExprEval.eval(
                new NumExpr.Bin(new NumExpr.Var(0), NumExpr.Op.DIVIDE, new NumExpr.Lit(2)), facts));
    }

    @Test
    void divideByZeroIsZeroNotNaN() {
        FactBuffer facts = new FactBuffer(1, 0, 0);
        facts.setNumber(0, 7.0);
        double result = NumExprEval.eval(
                new NumExpr.Bin(new NumExpr.Var(0), NumExpr.Op.DIVIDE, new NumExpr.Lit(0)), facts);
        assertEquals(0.0, result);
        assertTrue(Double.isFinite(result), "divide-by-zero must degrade to 0, never NaN (would poison the fold)");
    }

    @Test
    void unresolvedPlaceholderReadsAsNaN() {
        // A PAPI operand with no resolver parses to NaN — the fail-closed value for a numeric comparison.
        FactBuffer facts = new FactBuffer(0, 0, 0);
        assertTrue(Double.isNaN(NumExprEval.eval(new NumExpr.Papi("some_unknown_placeholder"), facts)));
    }
}
