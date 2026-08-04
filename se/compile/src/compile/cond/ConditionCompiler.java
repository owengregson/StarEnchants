package compile.cond;

import compile.model.cond.Cond;
import compile.model.cond.NumExpr;
import compile.model.cond.StrExpr;
import compile.resolve.PlatformResolvers;
import schema.diag.DiagCode;
import schema.diag.Diagnostics;
import schema.diag.Source;
import schema.grammar.expr.ArithOp;
import schema.grammar.expr.Cmp;
import schema.grammar.expr.Expr;
import schema.grammar.expr.ExprFn;
import schema.grammar.expr.StrOp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

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
 * <p>Never throws: a type error records a diagnostic and returns empty, so the ability lowers with
 * no condition rather than aborting the load (§7, §10).
 */
public final class ConditionCompiler {

    private final VarResolver vars;
    private final PlatformResolvers resolvers;

    /** No handle resolution: a {@code %scope.potion.<effect>%} token cannot resolve and is diagnosed. */
    public ConditionCompiler(VarResolver vars) {
        this(vars, PlatformResolvers.none());
    }

    /**
     * {@code resolvers} is the same Bukkit-free facade the resolve stage uses (§9): the keyed potion families
     * resolve their {@code <effect>} token to an interned handle HERE, at compile time, so only the resolved
     * id crosses into the runtime and this module stays Bukkit-free.
     */
    public ConditionCompiler(VarResolver vars, PlatformResolvers resolvers) {
        this.vars = Objects.requireNonNull(vars, "vars");
        this.resolvers = Objects.requireNonNull(resolvers, "resolvers");
    }

    /** Lower into a boolean {@link Cond}, or empty on a type error. */
    public Optional<Cond> compile(Expr expr, Diagnostics diags) {
        return bool(expr, diags);
    }

    /**
     * Lower as a numeric value into {@link NumExpr} — the entry point for an expression-valued effect
     * argument (§3.4). Empty on a type error (e.g. a comparison or string operand) so the caller keeps its constant default.
     */
    public Optional<NumExpr> numeric(Expr expr, Diagnostics diags) {
        return num(expr, diags);
    }

    // instanceof chains, not switch type patterns: the Java 17 floor lacks them (§11).
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
        if (e instanceof Expr.StringMatch m) {
            return stringMatch(m, diags);
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
        if (e instanceof Expr.Arith a) {
            return typeError(diags, a.source(), "an arithmetic expression is not a condition on its own",
                    "compare it, e.g. %actor.health% + 1 > 0");
        }
        if (e instanceof Expr.Neg n) {
            return typeError(diags, n.source(), "a negated value is not a condition on its own",
                    "compare it, e.g. -%damage% < 0");
        }
        if (e instanceof Expr.Call c) {
            return typeError(diags, c.source(), "a function value is not a condition on its own",
                    "compare it, e.g. " + c.fn().token() + "(…) > 0");
        }
        throw new IllegalStateException("unknown expression: " + e);
    }

    private Optional<NumExpr> num(Expr e, Diagnostics diags) {
        if (e instanceof Expr.NumberLit n) {
            return literal(n, diags);
        }
        if (e instanceof Expr.VarRef v) {
            return numVar(v, diags);
        }
        if (e instanceof Expr.Neg n) {
            return num(n.operand(), diags).map(NumExpr.Neg::new);
        }
        if (e instanceof Expr.Arith a) {
            Optional<NumExpr> l = num(a.left(), diags);
            Optional<NumExpr> r = num(a.right(), diags); // lower both, to collect every diagnostic
            return l.isPresent() && r.isPresent()
                    ? Optional.of(new NumExpr.Bin(l.get(), op(a.op()), r.get()))
                    : Optional.empty();
        }
        if (e instanceof Expr.Call c) {
            return call(c, diags);
        }
        Source src = e.source();
        if (e instanceof Expr.StringLit) {
            return numError(diags, src, "a string is not a number");
        }
        if (e instanceof Expr.BoolLit) {
            return numError(diags, src, "a boolean is not a number");
        }
        // And / Or / Not / Compare / StringMatch / Clause — boolean-valued, never a number.
        return numError(diags, src, "expected a numeric value but found a condition");
    }

    private Optional<NumExpr> literal(Expr.NumberLit n, Diagnostics diags) {
        try {
            return Optional.of(new NumExpr.Lit(Double.parseDouble(n.raw().trim())));
        } catch (NumberFormatException ex) {
            diags.error(DiagCode.E_COND_TYPE, "invalid number '" + n.raw() + "'", n.source());
            return Optional.empty();
        }
    }

    /**
     * {@code %victim.var.<name>%} — recognised by PREFIX before the PlaceholderAPI fallthrough, since
     * author-named vars can't be enumerated in the vocabulary. Without this arm the token would lower to a
     * PAPI passthrough and read null forever: a silent no-op instead of a working read.
     */
    private static NumExpr.EntityVar entityVar(Expr.VarRef v) {
        if (!"victim".equalsIgnoreCase(v.scope()) || v.name() == null) {
            return null;
        }
        String name = v.name();
        if (name.length() <= VAR_PREFIX.length() || !name.regionMatches(true, 0, VAR_PREFIX, 0, VAR_PREFIX.length())) {
            return null;
        }
        // The remainder is handed over whole — inner dots and all — so %victim.var.mark.beast% is one name.
        return new NumExpr.EntityVar(NumExpr.Scope.VICTIM, name.substring(VAR_PREFIX.length()));
    }

    private static final String VAR_PREFIX = "var.";

    private static final String POTION_PREFIX = "potion.";

    private static final String ENCHLEVEL_PREFIX = "enchlevel.";

    /** The activation entity a {@code scope} names, or {@code null} if it names neither side. */
    private static NumExpr.Scope entityScope(String scope) {
        if ("victim".equalsIgnoreCase(scope)) {
            return NumExpr.Scope.VICTIM;
        }
        return "actor".equalsIgnoreCase(scope) ? NumExpr.Scope.ACTOR : null;
    }

    /**
     * Whether this token is a keyed potion read — {@code %actor.potion.<effect>%} /
     * {@code %victim.potion.<effect>%}. Recognised by PREFIX for the same reason the {@code var.} family is:
     * the effect vocabulary is the platform's, not the var vocabulary's, so it cannot be enumerated in the
     * bindings, and without this arm the token would lower to a PAPI passthrough that reads null forever.
     */
    private static boolean isPotionRef(Expr.VarRef v) {
        return entityScope(v.scope()) != null && v.name() != null
                && v.name().length() > POTION_PREFIX.length()
                && v.name().regionMatches(true, 0, POTION_PREFIX, 0, POTION_PREFIX.length());
    }

    /** Resolve a recognised potion token's effect to its interned handle; unknown → diagnostic + empty. */
    private Optional<NumExpr> potionLevel(Expr.VarRef v, Diagnostics diags) {
        String token = v.name().substring(POTION_PREFIX.length());
        OptionalInt id = resolvers.potionEffect(token);
        if (id.isEmpty()) {
            diags.error(DiagCode.E_UNKNOWN_HANDLE,
                    "unknown potion effect '" + token + "' in '%" + token(v) + "%'", v.source(),
                    "use a potion effect name valid on the target version");
            return Optional.empty();
        }
        return Optional.of(new NumExpr.PotionLevel(entityScope(v.scope()), id.getAsInt()));
    }

    /**
     * Whether this token is a keyed worn-enchant read — {@code %actor.enchlevel.<key>%} /
     * {@code %victim.enchlevel.<key>%}. Prefix-recognised for the same reason the {@code var.}/{@code potion.}
     * families are: the enchant vocabulary is the pack's, not the var vocabulary's.
     */
    private static boolean isEnchantLevelRef(Expr.VarRef v) {
        return entityScope(v.scope()) != null && v.name() != null
                && v.name().length() > ENCHLEVEL_PREFIX.length()
                && v.name().regionMatches(true, 0, ENCHLEVEL_PREFIX, 0, ENCHLEVEL_PREFIX.length());
    }

    /**
     * The key crosses as a STRING, not a resolved id: the stable-key index is assigned by the ERASE stage,
     * which runs after this. An unknown key is NOT a diagnostic — like {@code %victim.var.<name>%} it reads 0,
     * since an enchant may legitimately be absent from a pack.
     */
    private static NumExpr.EnchantLevel enchantLevel(Expr.VarRef v) {
        // The remainder whole — inner dots and all — lower-cased to the worn map's canonical form.
        String key = v.name().substring(ENCHLEVEL_PREFIX.length()).toLowerCase(Locale.ROOT);
        return new NumExpr.EnchantLevel(entityScope(v.scope()), key);
    }

    private Optional<NumExpr> numVar(Expr.VarRef v, Diagnostics diags) {
        NumExpr.EntityVar entity = entityVar(v);
        if (entity != null) {
            return Optional.of(entity);
        }
        if (isPotionRef(v)) {
            return potionLevel(v, diags);
        }
        if (isEnchantLevelRef(v)) {
            return Optional.of(enchantLevel(v));
        }
        Optional<VarBinding> b = vars.resolve(v.scope(), v.name());
        if (b.isEmpty()) {
            return Optional.of(new NumExpr.Papi(token(v))); // unknown → PlaceholderAPI passthrough, parsed at runtime
        }
        return switch (b.get().kind()) {
            case NUM -> Optional.of(new NumExpr.Var(b.get().slot()));
            case STR -> numError(diags, v.source(), "string variable '" + token(v) + "' is not a number");
            case BOOL -> numError(diags, v.source(), "boolean variable '" + token(v) + "' is not a number");
        };
    }

    /** Every argument lowers (so one bad argument still reports the rest); any failure drops the whole call. */
    private Optional<NumExpr> call(Expr.Call c, Diagnostics diags) {
        List<NumExpr> args = new ArrayList<>(c.args().size());
        boolean ok = true;
        for (Expr arg : c.args()) {
            Optional<NumExpr> lowered = num(arg, diags);
            if (lowered.isPresent()) {
                args.add(lowered.get());
            } else {
                ok = false;
            }
        }
        return ok ? Optional.of(new NumExpr.Fn(fn(c.fn()), args)) : Optional.empty();
    }

    private static NumExpr.FnKind fn(ExprFn fn) {
        return switch (fn) {
            case MIN -> NumExpr.FnKind.MIN;
            case MAX -> NumExpr.FnKind.MAX;
            case CLAMP -> NumExpr.FnKind.CLAMP;
            case FLOOR -> NumExpr.FnKind.FLOOR;
            case RAND -> NumExpr.FnKind.RAND;
        };
    }

    private static NumExpr.Op op(ArithOp op) {
        return switch (op) {
            case ADD -> NumExpr.Op.ADD;
            case SUBTRACT -> NumExpr.Op.SUBTRACT;
            case MULTIPLY -> NumExpr.Op.MULTIPLY;
            case DIVIDE -> NumExpr.Op.DIVIDE;
        };
    }

    private static Optional<NumExpr> numError(Diagnostics diags, Source src, String message) {
        diags.error(DiagCode.E_COND_TYPE, message, src, "use a number, a %numeric variable%, or arithmetic over them");
        return Optional.empty();
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
            diags.error(DiagCode.E_COND_TYPE, "placeholder '" + token(v) + "' must be compared", v.source(),
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

    /** Lower a string-domain match ({@code contains}/{@code matchesregex}); both operands must be string-valued. */
    private Optional<Cond> stringMatch(Expr.StringMatch m, Diagnostics diags) {
        Operand l = operand(m.left(), diags);
        Operand r = operand(m.right(), diags);
        if (l == null || r == null) {
            return Optional.empty();
        }
        if (!isStringish(l) || !isStringish(r)) {
            return typeError(diags, m.source(), m.op().symbol() + " needs string operands",
                    "compare strings, e.g. %name% " + m.op().symbol() + " \"a\"");
        }
        if (m.op() == StrOp.CONTAINS) {
            // Like matchesregex, the alternative list is a literal — pre-split + lower-cased here (cold) so the
            // hot path never calls String#split / toLowerCase (performance-hot-paths; the ArchUnit gate bans them).
            if (!(m.right() instanceof Expr.StringLit alternatives)) {
                return typeError(diags, m.source(), "contains needs a literal alternative list",
                        "e.g. %actor.helditem% contains \"sword|axe\"");
            }
            return Optional.of(new Cond.StrContains(strOf(l), splitAlternatives(alternatives.value())));
        }
        // matchesregex: the pattern is a literal so a bad regex is caught at load, not per evaluation.
        if (!(m.right() instanceof Expr.StringLit literal)) {
            return typeError(diags, m.source(), "matchesregex needs a literal pattern",
                    "e.g. %name% matchesregex \"[a-z]+\"");
        }
        try {
            return Optional.of(new Cond.Regex(strOf(l), Pattern.compile(literal.value())));
        } catch (PatternSyntaxException bad) {
            return typeError(diags, m.source(), "invalid regex pattern: " + bad.getMessage(),
                    "fix the regular-expression syntax");
        }
    }

    /** Split a {@code |}-separated {@code contains} literal into lower-cased, non-empty alternatives (cold path). */
    private static String[] splitAlternatives(String literal) {
        String[] raw = literal.split("\\|");
        int kept = 0;
        for (String part : raw) {
            if (!part.isEmpty()) {
                raw[kept++] = part.toLowerCase(Locale.ROOT);
            }
        }
        return Arrays.copyOf(raw, kept);
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
            return varOperand(v, diags);
        }
        if (e instanceof Expr.Arith || e instanceof Expr.Neg || e instanceof Expr.Call) {
            // A numeric operand of a comparison, e.g. %actor.health% < max(%actor.maxhealth% / 2, 5).
            return num(e, diags).map(Operand::num).orElse(null);
        }
        // And / Or / Not / Compare → a (possibly parenthesised) boolean operand.
        return boolOperand(e, diags);
    }

    private Operand boolOperand(Expr e, Diagnostics diags) {
        return bool(e, diags).map(Operand::bool).orElse(null);
    }

    private Operand numLit(Expr.NumberLit n, Diagnostics diags) {
        return literal(n, diags).map(Operand::num).orElse(null);
    }

    private Operand varOperand(Expr.VarRef v, Diagnostics diags) {
        NumExpr.EntityVar entity = entityVar(v);
        if (entity != null) {
            return Operand.num(entity); // a counter is numeric: %victim.var.stacks% >= 3
        }
        if (isPotionRef(v)) {
            // Numeric too: %victim.potion.SLOW% > 0 is the "is it active" idiom, > 1 the "at least II" one.
            return potionLevel(v, diags).map(Operand::num).orElse(null);
        }
        if (isEnchantLevelRef(v)) {
            return Operand.num(enchantLevel(v)); // a level is numeric: %victim.enchlevel.metaphysical% >= 3
        }
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

    /** A string operand or a placeholder coercible to one (the operands {@code contains}/{@code matchesregex} accept). */
    private static boolean isStringish(Operand o) {
        return o.kind == OpKind.STR || o.kind == OpKind.DYN;
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
        diags.error(DiagCode.E_COND_TYPE, message, src, hint);
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
