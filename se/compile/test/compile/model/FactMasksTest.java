package compile.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.model.cond.Cond;
import compile.model.cond.NumExpr;
import compile.model.cond.StrExpr;
import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.regex.Pattern;
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

    /**
     * The structural exhaustiveness guard (ADR-0039). {@link FactMasks} walks the {@code sealed}
     * {@link Cond}/{@link NumExpr}/{@link StrExpr} hierarchies with an {@code else throw} rather than a
     * compiler-checked {@code switch} (the module's class floor is Java 17, which lacks Java 21's
     * sealed-switch exhaustiveness). This reflectively enumerates every permit of each hierarchy, builds a
     * synthetic instance, and routes it through the walk: a permit added without a matching branch trips the
     * {@code else throw} here — the same "add a node, the mask forgets its slots" bug a compile error would
     * catch, turned into a red test instead of a silent wrong-gate at runtime.
     */
    @Test
    void everySealedAstPermitIsWalkedWithoutThrowing() {
        Class<?>[] condPermits = Cond.class.getPermittedSubclasses();
        Class<?>[] numPermits = NumExpr.class.getPermittedSubclasses();
        Class<?>[] strPermits = StrExpr.class.getPermittedSubclasses();
        assertTrue(condPermits.length > 0 && numPermits.length > 0 && strPermits.length > 0,
                "sealed AST types must permit subtypes — a zero-length list would make this guard vacuous");

        for (Class<?> permit : condPermits) {
            Cond node = (Cond) synthetic(permit);
            assertDoesNotThrow(() -> FactMasks.of(gate(node), NO_EFFECTS),
                    () -> "FactMasks#cond does not handle Cond permit " + permit.getName());
        }
        for (Class<?> permit : numPermits) {
            NumExpr node = (NumExpr) synthetic(permit);
            // A NumExpr is reached as an expression-valued effect arg.
            CompiledEffect effect = new CompiledEffect("EFFECT", Args.empty().with("v", node),
                    CompiledSelector.SELF, 0, Affinity.CONTEXT_LOCAL);
            assertDoesNotThrow(() -> FactMasks.of(null, new CompiledEffect[] {effect}),
                    () -> "FactMasks#num does not handle NumExpr permit " + permit.getName());
        }
        for (Class<?> permit : strPermits) {
            StrExpr node = (StrExpr) synthetic(permit);
            // A StrExpr is only reached through a Cond; StrCmp routes both operands into str().
            Cond wrap = new Cond.StrCmp(node, true, node);
            assertDoesNotThrow(() -> FactMasks.of(gate(wrap), NO_EFFECTS),
                    () -> "FactMasks#str does not handle StrExpr permit " + permit.getName());
        }
    }

    /** Build a permit via its canonical record constructor, filling each component with a synthetic value. */
    private static Object synthetic(Class<?> permit) {
        assertTrue(permit.isRecord(), () -> "AST permit " + permit.getName() + " is expected to be a record");
        RecordComponent[] components = permit.getRecordComponents();
        Class<?>[] paramTypes = new Class<?>[components.length];
        Object[] args = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            paramTypes[i] = components[i].getType();
            args[i] = syntheticValue(paramTypes[i]);
        }
        try {
            Constructor<?> ctor = permit.getDeclaredConstructor(paramTypes);
            ctor.setAccessible(true);
            return ctor.newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot construct synthetic " + permit.getName(), e);
        }
    }

    private static Object syntheticValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == double.class) {
            return 0.0;
        }
        if (type == String.class) {
            return "x";
        }
        if (type == String[].class) {
            return new String[] {"x"};
        }
        if (type == Pattern.class) {
            return Pattern.compile("x");
        }
        if (type.isEnum()) {
            return type.getEnumConstants()[0]; // e.g. Cmp / NumExpr.Op — never read by the walk
        }
        // Leaf operands for the recursive AST param types (slot-free, so they walk without throwing).
        if (type == Cond.class) {
            return new Cond.BoolLit(false);
        }
        if (type == NumExpr.class) {
            return new NumExpr.Lit(0);
        }
        if (type == StrExpr.class) {
            return new StrExpr.Lit("");
        }
        if (type == java.util.List.class) {
            return java.util.List.of(new NumExpr.Lit(0)); // NumExpr.Fn's operands — the only list-valued component
        }
        throw new IllegalStateException("no synthetic value for AST constructor parameter type " + type.getName()
                + "; extend syntheticValue(...) when introducing a new node component shape");
    }
}
