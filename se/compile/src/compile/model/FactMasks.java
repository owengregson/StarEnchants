package compile.model;

import compile.model.cond.Cond;
import compile.model.cond.NumExpr;
import compile.model.cond.StrExpr;

/**
 * Derives an ability's {@link FactMask} at compile time (ADR-0039): the union of every {@code FactBuffer}
 * slot its condition AST and its expression-valued effect args read. The runtime's only slot readers are
 * {@code ConditionEvaluator} (flags/strings) and {@code NumExprEval} (numbers), and both read exactly the
 * {@code Var} nodes walked here, so the derived mask covers every hot-path read precisely.
 */
public final class FactMasks {

    private FactMasks() {
    }

    /** As {@link #of(CompiledCondition, NumExpr, CompiledEffect[])} for an ability with a constant chance. */
    public static FactMask of(CompiledCondition condition, CompiledEffect[] effects) {
        return of(condition, null, effects);
    }

    /**
     * The union of {@code condition}'s slots, the chance expression's, and every effect arg's expression
     * slots; {@link FactMask#ALL} if a slot overflows a 64-bit space.
     */
    public static FactMask of(CompiledCondition condition, NumExpr chanceExpr, CompiledEffect[] effects) {
        Acc acc = new Acc();
        if (condition != null) {
            acc.cond(condition.root());
        }
        if (chanceExpr != null) {
            acc.num(chanceExpr); // gate 8 reads it from the same buffer, so its facts must be populated too
        }
        for (CompiledEffect effect : effects) {
            for (Object value : effect.args().asMap().values()) {
                if (value instanceof NumExpr expr) {
                    acc.num(expr); // e.g. DAMAGE_MOD:...:%combo% reads the combo number slot at run time
                }
            }
        }
        return acc.mask();
    }

    /** Mutable slot accumulator: any slot {@code >= 64} trips {@link #overflow}, forcing {@link FactMask#ALL}. */
    private static final class Acc {
        private long numBits;
        private long flagBits;
        private long strBits;
        private boolean overflow;

        FactMask mask() {
            return overflow ? FactMask.ALL : new FactMask(numBits, flagBits, strBits);
        }

        // These three walks branch over EVERY permit of their sealed type; the closing `else throw` is the
        // structural exhaustiveness guard. Because the compile module's class floor is Java 17 (root
        // build.gradle.kts), a `switch` over sealed permits can't get compiler-checked exhaustiveness — that
        // needs Java 21 semantics — so a new subtype cannot be a compile error here. Instead the `else`
        // turns a forgotten subtype into a loud throw, and FactMasksTest walks a synthetic instance of every
        // permit to fail the build the moment one is unhandled. A missing branch would drop that node's slots
        // from the mask, so the demand-driven populator would skip a referenced fact — a silent wrong-gate.
        void cond(Cond node) {
            if (node instanceof Cond.And a) {
                cond(a.left());
                cond(a.right());
            } else if (node instanceof Cond.Or o) {
                cond(o.left());
                cond(o.right());
            } else if (node instanceof Cond.Not n) {
                cond(n.operand());
            } else if (node instanceof Cond.NumCmp c) {
                num(c.left());
                num(c.right());
            } else if (node instanceof Cond.StrCmp c) {
                str(c.left());
                str(c.right());
            } else if (node instanceof Cond.BoolCmp c) {
                cond(c.left());
                cond(c.right());
            } else if (node instanceof Cond.StrContains c) {
                str(c.left());
            } else if (node instanceof Cond.Regex c) {
                str(c.left());
            } else if (node instanceof Cond.BoolVar v) {
                flagBits |= bit(v.slot());
            } else if (node instanceof Cond.BoolLit || node instanceof Cond.BoolPapi) {
                // Reference no fact slot (a PAPI token is resolved through the lazy resolver).
            } else {
                throw new IllegalStateException("unhandled node: " + node.getClass());
            }
        }

        void num(NumExpr node) {
            if (node instanceof NumExpr.Var v) {
                numBits |= bit(v.slot());
            } else if (node instanceof NumExpr.Bin b) {
                num(b.left());
                num(b.right());
            } else if (node instanceof NumExpr.Neg n) {
                num(n.operand());
            } else if (node instanceof NumExpr.Fn f) {
                for (NumExpr arg : f.args()) {
                    num(arg); // a function reads nothing itself — only whatever its arguments read
                }
            } else if (node instanceof NumExpr.Lit || node instanceof NumExpr.Papi
                    || node instanceof NumExpr.EntityVar || node instanceof NumExpr.PotionLevel) {
                // Reference no fact slot (PAPI tokens, entity vars and potion levels all resolve through
                // lazy readers, so they cost nothing until the node is actually reached).
            } else {
                throw new IllegalStateException("unhandled node: " + node.getClass());
            }
        }

        void str(StrExpr node) {
            if (node instanceof StrExpr.Var v) {
                strBits |= bit(v.slot());
            } else if (node instanceof StrExpr.Lit || node instanceof StrExpr.Papi) {
                // Reference no fact slot.
            } else {
                throw new IllegalStateException("unhandled node: " + node.getClass());
            }
        }

        private long bit(int slot) {
            if (slot < 0 || slot >= Long.SIZE) {
                overflow = true; // unrepresentable → the whole mask degrades to ALL, never an aliased bit
                return 0L;
            }
            return 1L << slot;
        }
    }
}
