package compile.model;

import compile.model.cond.Cond;
import compile.model.cond.NumExpr;
import compile.model.cond.StrExpr;
import schema.spec.Args;

/**
 * Derives an ability's {@link FactMask} at compile time (ADR-0039): the union of every {@code FactBuffer}
 * slot its condition AST and its expression-valued effect args read. The runtime's only slot readers are
 * {@code ConditionEvaluator} (flags/strings) and {@code NumExprEval} (numbers), and both read exactly the
 * {@code Var} nodes walked here, so the derived mask covers every hot-path read precisely.
 */
public final class FactMasks {

    private FactMasks() {
    }

    /** The union of {@code condition}'s slots and every effect arg's expression slots; {@link FactMask#ALL} if a slot overflows a 64-bit space. */
    public static FactMask of(CompiledCondition condition, CompiledEffect[] effects) {
        Acc acc = new Acc();
        if (condition != null) {
            acc.cond(condition.root());
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
            }
            // BoolLit / BoolPapi reference no fact slot (a PAPI token is resolved through the lazy resolver).
        }

        void num(NumExpr node) {
            if (node instanceof NumExpr.Var v) {
                numBits |= bit(v.slot());
            } else if (node instanceof NumExpr.Bin b) {
                num(b.left());
                num(b.right());
            } else if (node instanceof NumExpr.Neg n) {
                num(n.operand());
            }
            // Lit / Papi reference no fact slot.
        }

        void str(StrExpr node) {
            if (node instanceof StrExpr.Var v) {
                strBits |= bit(v.slot());
            }
            // Lit / Papi reference no fact slot.
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
