package engine.condition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.model.cond.NumExpr;
import java.util.ArrayList;
import java.util.List;
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
    void potionLevelReadsAmplifierPlusOnePerScopeAndZeroWhenAbsent() {
        FactBuffer facts = new FactBuffer(0, 0, 0);
        facts.potionLevels(new PotionLevels() {
            @Override
            public int actorLevel(int potionEffectId) {
                return potionEffectId == 7 ? 2 : 0; // Speed II on the actor
            }

            @Override
            public int victimLevel(int potionEffectId) {
                return 0;
            }
        });
        // amplifier+1, so `> 0` is the "is it active" idiom and `> 1` the "at least II" one.
        assertEquals(2.0, NumExprEval.eval(new NumExpr.PotionLevel(NumExpr.Scope.ACTOR, 7), facts));
        assertEquals(0.0, NumExprEval.eval(new NumExpr.PotionLevel(NumExpr.Scope.ACTOR, 8), facts));
        // The scope is not decorative: reading the victim must not answer with the actor's effects.
        assertEquals(0.0, NumExprEval.eval(new NumExpr.PotionLevel(NumExpr.Scope.VICTIM, 7), facts));
        // With no reader installed (a synthetic run) every read is 0 rather than NaN.
        facts.clear();
        assertEquals(0.0, NumExprEval.eval(new NumExpr.PotionLevel(NumExpr.Scope.ACTOR, 7), facts));
    }

    @Test
    void enchantLevelReadsTheWornLevelPerScopeAndZeroWhenAbsent() {
        FactBuffer facts = new FactBuffer(0, 0, 0);
        facts.enchantLevels(new EnchantLevels() {
            @Override
            public int actorLevel(String key) {
                return "solitude".equals(key) ? 3 : 0; // Solitude III co-held by the actor
            }

            @Override
            public int victimLevel(String key) {
                return 0;
            }
        });
        // The worn level, so `> 0` is the "has it" idiom and `>= 3` the "at least III" one.
        assertEquals(3.0, NumExprEval.eval(new NumExpr.EnchantLevel(NumExpr.Scope.ACTOR, "solitude"), facts));
        assertEquals(0.0, NumExprEval.eval(new NumExpr.EnchantLevel(NumExpr.Scope.ACTOR, "sticky"), facts));
        // The scope is not decorative: reading the victim must not answer with the actor's enchants.
        assertEquals(0.0, NumExprEval.eval(new NumExpr.EnchantLevel(NumExpr.Scope.VICTIM, "solitude"), facts));
        // With no reader installed (a synthetic run) every read is 0 rather than NaN.
        facts.clear();
        assertEquals(0.0, NumExprEval.eval(new NumExpr.EnchantLevel(NumExpr.Scope.ACTOR, "solitude"), facts));
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

    private static NumExpr fn(NumExpr.FnKind kind, double... args) {
        List<NumExpr> operands = new ArrayList<>();
        for (double arg : args) {
            operands.add(new NumExpr.Lit(arg));
        }
        return new NumExpr.Fn(kind, operands);
    }

    @Test
    void functionsFoldTheirArguments() {
        FactBuffer facts = new FactBuffer(0, 0, 0);
        assertEquals(2.0, NumExprEval.eval(fn(NumExpr.FnKind.MIN, 2, 3), facts));
        assertEquals(3.0, NumExprEval.eval(fn(NumExpr.FnKind.MAX, 2, 3), facts));
        assertEquals(4.0, NumExprEval.eval(fn(NumExpr.FnKind.CLAMP, 5, 0, 4), facts));
        assertEquals(0.0, NumExprEval.eval(fn(NumExpr.FnKind.CLAMP, -1, 0, 4), facts));
        assertEquals(2.0, NumExprEval.eval(fn(NumExpr.FnKind.FLOOR, 2.9), facts));
        assertEquals(-3.0, NumExprEval.eval(fn(NumExpr.FnKind.FLOOR, -2.1), facts));
    }

    @Test
    void randScalesTheInjectedSupplierIntoTheHalfOpenSpan() {
        // Randomness is an injected DoubleSupplier on the evaluation environment, never an inline
        // ThreadLocalRandom — that is what makes a rand()-bearing ability reproducible under test.
        FactBuffer facts = new FactBuffer(0, 0, 0);
        facts.randomSource(() -> 0.5);
        assertEquals(2.0, NumExprEval.eval(fn(NumExpr.FnKind.RAND, 1, 3), facts));
        facts.randomSource(() -> 0.0);
        assertEquals(1.0, NumExprEval.eval(fn(NumExpr.FnKind.RAND, 1, 3), facts)); // lo is inclusive
        facts.randomSource(() -> 0.999);
        assertTrue(NumExprEval.eval(fn(NumExpr.FnKind.RAND, 1, 3), facts) < 3.0); // hi is exclusive
    }

    @Test
    void randWithoutAnInjectedSourceIsDeterministic() {
        // The engine default draws nothing (mirrors Activation's chanceRoll default): production wires the
        // real source, so an unwired evaluation can never silently invent a value.
        FactBuffer facts = new FactBuffer(0, 0, 0);
        assertEquals(1.0, NumExprEval.eval(fn(NumExpr.FnKind.RAND, 1, 3), facts));
    }

    @Test
    void functionArgumentsAreThemselvesExpressions() {
        FactBuffer facts = new FactBuffer(1, 0, 0);
        facts.setNumber(0, 8.0);
        // min(50, %recentattackers% * 10) -> 50 at 8 attackers, 20 at 2
        NumExpr capped = new NumExpr.Fn(NumExpr.FnKind.MIN, List.of(new NumExpr.Lit(50),
                new NumExpr.Bin(new NumExpr.Var(0), NumExpr.Op.MULTIPLY, new NumExpr.Lit(10))));
        assertEquals(50.0, NumExprEval.eval(capped, facts));
        facts.setNumber(0, 2.0);
        assertEquals(20.0, NumExprEval.eval(capped, facts));
    }
}
