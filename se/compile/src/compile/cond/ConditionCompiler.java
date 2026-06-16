package compile.cond;

import compile.model.cond.Cond;
import compile.model.cond.NumExpr;
import compile.model.cond.StrExpr;
import schema.diag.Diagnostics;
import schema.diag.Source;
import schema.grammar.expr.Cmp;
import schema.grammar.expr.Expr;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * Lowers an untyped condition {@link Expr} into the typed, slot-resolved
 * {@link Cond} IR (docs/architecture.md §3.4): variables resolve to dense
 * {@code FactBuffer} slots via the injected {@link VarResolver}, literals are
 * pre-parsed, and every operand is type-checked. An unknown variable is not an error —
 * it becomes a PlaceholderAPI token resolved at runtime.
 *
 * <p>Type rules (mismatches are file/line diagnostics, never exceptions):
 * <ul>
 *   <li>numeric operands admit all six comparators;</li>
 *   <li>string operands admit only {@code ==}/{@code !=};</li>
 *   <li>boolean operands admit only {@code ==}/{@code !=} (and stand alone as a gate);</li>
 *   <li>a PlaceholderAPI token coerces to the other operand's type, defaulting to a
 *       string comparison when both sides are placeholders;</li>
 *   <li>the whole condition must be boolean — a bare number/string/placeholder is an
 *       error ("compare it").</li>
 * </ul>
 *
 * <p>Never throws: on any type error it records a diagnostic and returns empty, so the
 * ability lowers with no condition rather than aborting the load (§7, §10).
 */
public final class ConditionCompiler {

    private final VarResolver vars;

    public ConditionCompiler(VarResolver vars) {
        this.vars = Objects.requireNonNull(vars, "vars");
    }

    /** Lower a parsed expression into a boolean {@link Cond}, or empty on a type error. */
    public Optional<Cond> compile(Expr expr, Diagnostics diags) {
        return bool(expr, diags);
    }

    // ── Lower an expression expected to yield a boolean (the gate) ──

    // instanceof pattern chains rather than switch type patterns (the Java 17 floor
    // does not have switch patterns; §11).
    private Optional<Cond> bool(Expr e, Diagnostics diags) {
        if (e instanceof Expr.And a) {
            return both(a.left(), a.right(), diags, Cond.And::new);
        }
        if (e instanceof Expr.Or o) {
            return both(o.left(), o.right(), diags, Cond.Or::new);
        }
        if (e instanceof Expr.Not n) {
            return bool(n.operand(), diags).map(Cond.Not::new);
        }
        if (e instanceof Expr.Compare c) {
            return compare(c, diags);
        }
        if (e instanceof Expr.BoolLit b) {
            return Optional.of(new Cond.BoolLit(b.value()));
        }
        if (e instanceof Expr.VarRef v) {
            return boolVar(v, diags);
        }
        if (e instanceof Expr.NumberLit n) {
            return typeError(diags, n.source(), "a number is not a condition on its own",
                    "compare it, e.g. %damage% > 5");
        }
        if (e instanceof Expr.StringLit s) {
            return typeError(diags, s.source(), "a string is not a condition on its own",
                    "compare it, e.g. %name% == \"steve\"");
        }
        throw new IllegalStateException("unknown expression: " + e);
    }

    private Optional<Cond> both(Expr l, Expr r, Diagnostics diags, BiFunction<Cond, Cond, Cond> ctor) {
        Optional<Cond> lc = bool(l, diags);
        Optional<Cond> rc = bool(r, diags); // always lower both, to collect every diagnostic
        return lc.isPresent() && rc.isPresent()
                ? Optional.of(ctor.apply(lc.get(), rc.get()))
                : Optional.empty();
    }

    private Optional<Cond> boolVar(Expr.VarRef v, Diagnostics diags) {
        Optional<VarBinding> b = vars.resolve(v.scope(), v.name());
        if (b.isEmpty()) {
            diags.error("E_COND_TYPE", "placeholder '" + token(v) + "' must be compared", v.source(),
                    "e.g. %" + token(v) + "% == \"yes\"");
            return Optional.empty();
        }
        return switch (b.get().kind()) {
            case BOOL -> Optional.of(new Cond.BoolVar(b.get().slot()));
            case NUM -> typeError(diags, v.source(), "numeric variable '" + token(v) + "' is not a condition",
                    "compare it, e.g. %" + token(v) + "% > 0");
            case STR -> typeError(diags, v.source(), "string variable '" + token(v) + "' is not a condition",
                    "compare it, e.g. %" + token(v) + "% == \"x\"");
        };
    }

    // ── Comparison lowering with operand typing + coercion ──

    private Optional<Cond> compare(Expr.Compare c, Diagnostics diags) {
        Operand l = operand(c.left(), diags);
        Operand r = operand(c.right(), diags);
        if (l == null || r == null) {
            return Optional.empty();
        }
        Cmp op = c.op();
        Source src = c.source();

        boolean eq = op == Cmp.EQ;
        if (l.kind == OpKind.BOOL || r.kind == OpKind.BOOL) {
            // The other side must be a boolean or a placeholder coerced to boolean
            // (e.g. %essentials_afk% == true); numbers/strings vs boolean are mismatches.
            if (!isBoolish(l) || !isBoolish(r)) {
                return mismatch(diags, src, l, r);
            }
            if (op != Cmp.EQ && op != Cmp.NE) {
                return typeError(diags, src, "booleans support only == or !=", "use == or !=");
            }
            return Optional.of(new Cond.BoolCmp(boolOf(l), eq, boolOf(r)));
        }
        if (l.kind == OpKind.NUM || r.kind == OpKind.NUM) {
            if (l.kind == OpKind.STR || r.kind == OpKind.STR) {
                return mismatch(diags, src, l, r);
            }
            return Optional.of(new Cond.NumCmp(numOf(l), op, numOf(r)));
        }
        // string vs string, string vs papi, or papi vs papi → string (in)equality
        if (op != Cmp.EQ && op != Cmp.NE) {
            return typeError(diags, src, "strings support only == or !=", "use == or !=");
        }
        return Optional.of(new Cond.StrCmp(strOf(l), eq, strOf(r)));
    }

    /** Lower an expression as a comparison operand (atom, parenthesised boolean, or nested compare). */
    private Operand operand(Expr e, Diagnostics diags) {
        if (e instanceof Expr.NumberLit n) {
            return numLit(n, diags);
        }
        if (e instanceof Expr.StringLit s) {
            return Operand.str(new StrExpr.Lit(s.value()));
        }
        if (e instanceof Expr.BoolLit b) {
            return Operand.bool(new Cond.BoolLit(b.value()));
        }
        if (e instanceof Expr.VarRef v) {
            return varOperand(v);
        }
        // And / Or / Not / Compare → a (possibly parenthesised) boolean operand.
        return boolOperand(e, diags);
    }

    private Operand boolOperand(Expr e, Diagnostics diags) {
        return bool(e, diags).map(Operand::bool).orElse(null);
    }

    private Operand numLit(Expr.NumberLit n, Diagnostics diags) {
        try {
            return Operand.num(new NumExpr.Lit(Double.parseDouble(n.raw().trim())));
        } catch (NumberFormatException ex) {
            diags.error("E_COND_TYPE", "invalid number '" + n.raw() + "'", n.source());
            return null;
        }
    }

    private Operand varOperand(Expr.VarRef v) {
        Optional<VarBinding> b = vars.resolve(v.scope(), v.name());
        if (b.isEmpty()) {
            return Operand.papi(token(v)); // unknown → PlaceholderAPI passthrough
        }
        return switch (b.get().kind()) {
            case NUM -> Operand.num(new NumExpr.Var(b.get().slot()));
            case STR -> Operand.str(new StrExpr.Var(b.get().slot()));
            case BOOL -> Operand.bool(new Cond.BoolVar(b.get().slot()));
        };
    }

    private static NumExpr numOf(Operand o) {
        return o.kind == OpKind.NUM ? o.num : new NumExpr.Papi(o.token);
    }

    private static StrExpr strOf(Operand o) {
        return o.kind == OpKind.STR ? o.str : new StrExpr.Papi(o.token);
    }

    /** A boolean operand or a placeholder coercible to one. */
    private static boolean isBoolish(Operand o) {
        return o.kind == OpKind.BOOL || o.kind == OpKind.DYN;
    }

    private static Cond boolOf(Operand o) {
        return o.kind == OpKind.BOOL ? o.bool : new Cond.BoolPapi(o.token);
    }

    private static Optional<Cond> mismatch(Diagnostics diags, Source src, Operand l, Operand r) {
        return typeError(diags, src, "cannot compare " + l.kind.label() + " with " + r.kind.label(),
                "compare values of the same type");
    }

    /** Record a condition type error and return empty, so callers can {@code return typeError(...)}. */
    private static Optional<Cond> typeError(Diagnostics diags, Source src, String message, String hint) {
        diags.error("E_COND_TYPE", message, src, hint);
        return Optional.empty();
    }

    private static String token(Expr.VarRef v) {
        return v.scope() == null ? v.name() : v.scope() + "." + v.name();
    }

    private enum OpKind {
        NUM("a number"), STR("a string"), BOOL("a boolean"), DYN("a placeholder");

        private final String label;

        OpKind(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    private static final class Operand {
        final OpKind kind;
        final NumExpr num;
        final StrExpr str;
        final Cond bool;
        final String token;

        private Operand(OpKind kind, NumExpr num, StrExpr str, Cond bool, String token) {
            this.kind = kind;
            this.num = num;
            this.str = str;
            this.bool = bool;
            this.token = token;
        }

        static Operand num(NumExpr n) {
            return new Operand(OpKind.NUM, n, null, null, null);
        }

        static Operand str(StrExpr s) {
            return new Operand(OpKind.STR, null, s, null, null);
        }

        static Operand bool(Cond b) {
            return new Operand(OpKind.BOOL, null, null, b, null);
        }

        static Operand papi(String t) {
            return new Operand(OpKind.DYN, null, null, null, t);
        }
    }
}
