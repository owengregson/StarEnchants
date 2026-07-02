package compile.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.model.cond.Cond;
import compile.model.cond.NumExpr;
import compile.model.cond.StrExpr;
import schema.diag.Source;
import schema.grammar.expr.Cmp;
import schema.spec.Args;
import org.junit.jupiter.api.Test;

/**
 * The derived {@link FactMask} must cover EXACTLY the {@code FactBuffer} slots an ability reads — the runtime
 * populator gates on it, so a missing bit would leave a referenced fact unpopulated (a silent wrong-gate bug).
 * Slot numbers here are test-owned inputs (any index), not the production vocabulary, so this pins the walk itself.
 */
class FactMasksTest {

    private static final CompiledEffect[] NO_EFFECTS = new CompiledEffect[0];

    private static CompiledCondition gate(Cond root) {
        return CompiledCondition.gate(root, Source.UNKNOWN);
    }

    @Test
    void nullConditionAndNoEffectsReferenceNothing() {
        assertEquals(FactMask.NONE, FactMasks.of(null, NO_EFFECTS));
    }

    @Test
    void oneNumericFactSetsOnlyItsNumberBit() {
        // %distance% at number slot 5 > 3
        FactMask mask = FactMasks.of(gate(new Cond.NumCmp(new NumExpr.Var(5), Cmp.GT, new NumExpr.Lit(3))), NO_EFFECTS);

        assertTrue(mask.readsNum(5));
        assertFalse(mask.readsNum(4));
        assertEquals(0L, mask.flag());
        assertEquals(0L, mask.str());
    }

    @Test
    void severalFactsAcrossSpacesEachSetTheirOwnBit() {
        // (num[2] < 10) AND (flag[1]) AND (str[3] == "ICE")
        Cond root = new Cond.And(
                new Cond.And(
                        new Cond.NumCmp(new NumExpr.Var(2), Cmp.LT, new NumExpr.Lit(10)),
                        new Cond.BoolVar(1)),
                new Cond.StrCmp(new StrExpr.Var(3), true, new StrExpr.Lit("ICE")));

        FactMask mask = FactMasks.of(gate(root), NO_EFFECTS);

        assertTrue(mask.readsNum(2));
        assertTrue(mask.readsFlag(1));
        assertTrue(mask.readsStr(3));
        assertFalse(mask.readsNum(1)); // flag slot 1 must NOT leak into the number space
        assertFalse(mask.readsStr(1));
    }

    @Test
    void nestedArithmeticAndContainsWalkIntoOperands() {
        // num[7] > (num[8] * 2)  — both operands are referenced
        Cond arith = new Cond.NumCmp(new NumExpr.Var(7), Cmp.GT,
                new NumExpr.Bin(new NumExpr.Var(8), NumExpr.Op.MULTIPLY, new NumExpr.Lit(2)));
        // %actor.groundblock% (str[4]) contains "ICE"
        Cond contains = new Cond.StrContains(new StrExpr.Var(4), new String[] {"ice"});

        FactMask mask = FactMasks.of(gate(new Cond.And(arith, contains)), NO_EFFECTS);

        assertTrue(mask.readsNum(7));
        assertTrue(mask.readsNum(8));
        assertTrue(mask.readsStr(4));
    }

    @Test
    void expressionValuedEffectArgUnionsItsNumberSlots() {
        // DAMAGE_MOD:...:%combo%(num[6]) * 10 — the arg reads number slot 6 at run time, so it must be populated
        NumExpr scaled = new NumExpr.Bin(new NumExpr.Var(6), NumExpr.Op.MULTIPLY, new NumExpr.Lit(10));
        CompiledEffect effect = new CompiledEffect("DAMAGE_MOD", Args.empty().with("amount", scaled),
                CompiledSelector.SELF, 0, Affinity.CONTEXT_LOCAL);

        FactMask mask = FactMasks.of(null, new CompiledEffect[] {effect});

        assertTrue(mask.readsNum(6));
    }

    @Test
    void literalAndPapiNodesReferenceNoSlot() {
        Cond papi = new Cond.NumCmp(new NumExpr.Papi("some_placeholder"), Cmp.GE, new NumExpr.Lit(1));
        assertEquals(FactMask.NONE, FactMasks.of(gate(papi), NO_EFFECTS));
    }

    @Test
    void aSlotBeyond64DegradesTheWholeMaskToAll() {
        // Unrepresentable in a 64-bit space → ALL (populate everything), never an aliased bit.
        FactMask mask = FactMasks.of(gate(new Cond.BoolVar(64)), NO_EFFECTS);
        assertSame(FactMask.ALL, mask);
    }
}
