package compile.cond;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.model.cond.Cond;
import compile.model.cond.NumExpr;
import compile.model.cond.StrExpr;
import compile.resolve.PlatformResolvers;
import schema.diag.DiagCode;
import schema.diag.Diagnostics;
import schema.diag.Source;
import schema.grammar.expr.Cmp;
import schema.grammar.expr.Expr;
import schema.grammar.expr.ExprFn;
import schema.grammar.expr.ExprParser;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class ConditionCompilerTest {

    private static final Source SRC = Source.of("enchants.yml", 1, 1);

    private static final Map<String, VarBinding> VOCAB = Map.of(
            "victim.health", new VarBinding(VarKind.NUM, 0),
            "actor.health", new VarBinding(VarKind.NUM, 1),
            "damage", new VarBinding(VarKind.NUM, 2),
            "sneaking", new VarBinding(VarKind.BOOL, 0),
            "blocking", new VarBinding(VarKind.BOOL, 1),
            "name", new VarBinding(VarKind.STR, 0));

    private static final VarResolver VARS =
            (scope, name) -> Optional.ofNullable(VOCAB.get(scope == null ? name : scope + "." + name));

    private static Cond lower(String expr, Diagnostics d) {
        Expr ast = ExprParser.parse(expr, SRC, d).orElseThrow();
        return new ConditionCompiler(VARS).compile(ast, d).orElseThrow();
    }

    private static Diagnostics lowerExpectingError(String expr) {
        Diagnostics d = new Diagnostics();
        Expr ast = ExprParser.parse(expr, SRC, d).orElseThrow();
        Optional<Cond> result = new ConditionCompiler(VARS).compile(ast, d);
        assertTrue(result.isEmpty(), "expected lowering to fail for: " + expr);
        assertTrue(d.hasErrors());
        return d;
    }

    @Test
    void numericVariableComparedToLiteral() {
        Diagnostics d = new Diagnostics();
        Cond c = lower("%victim.health% < 5", d);
        assertFalse(d.hasErrors());
        Cond.NumCmp cmp = assertInstanceOf(Cond.NumCmp.class, c);
        assertEquals(new NumExpr.Var(0), cmp.left());
        assertEquals(Cmp.LT, cmp.op());
        assertEquals(new NumExpr.Lit(5.0), cmp.right());
    }

    @Test
    void containsLowersToStrContains() {
        Diagnostics d = new Diagnostics();
        Cond c = lower("%name% contains \"A|b\"", d);
        assertFalse(d.hasErrors());
        Cond.StrContains sc = assertInstanceOf(Cond.StrContains.class, c);
        assertEquals(new StrExpr.Var(0), sc.left());
        // Alternatives are split on '|' and lower-cased once at load (empties dropped), so the hot path scans them directly.
        assertArrayEquals(new String[] {"a", "b"}, sc.alternatives());
    }

    @Test
    void containsAcceptsPlaceholderOperands() {
        Diagnostics d = new Diagnostics();
        Cond c = lower("%some_papi% contains \"x\"", d); // unknown var → PlaceholderAPI passthrough
        assertFalse(d.hasErrors());
        assertInstanceOf(Cond.StrContains.class, c);
    }

    @Test
    void matchesRegexCompilesItsLiteralPatternAtLoad() {
        Diagnostics d = new Diagnostics();
        Cond c = lower("%name% matchesregex \"[a-z]+\"", d);
        assertFalse(d.hasErrors());
        Cond.Regex r = assertInstanceOf(Cond.Regex.class, c);
        assertEquals(new StrExpr.Var(0), r.left());
        assertTrue(r.pattern().matcher("abc").matches());
    }

    /** Every ill-typed condition is rejected as E_COND_TYPE — one table instead of eight near-identical tests. */
    @ParameterizedTest
    @ValueSource(strings = {
            "%name% matchesregex %name%", // regex pattern must be a literal, not a variable
            "%name% matchesregex \"[\"",  // invalid regex literal
            "%name% contains %name%",     // contains alternatives must be a literal (pre-split at load)
            "%damage% contains \"x\"",    // string op on a numeric operand
            "%name% < \"x\"",             // ordering a string
            "%damage% == %name%",         // number compared with string
            "5",                          // a bare number is not a condition
            "%damage%",                   // a bare numeric variable is not a condition
            "%some_papi%",                // a bare placeholder must be compared
    })
    void illTypedConditionIsRejectedAsCondTypeError(String expr) {
        Diagnostics d = lowerExpectingError(expr);
        assertTrue(d.all().get(0).is(DiagCode.E_COND_TYPE), () -> d.all().toString());
    }

    @Test
    void numericVariableComparedToVariable() {
        Diagnostics d = new Diagnostics();
        Cond.NumCmp cmp = assertInstanceOf(Cond.NumCmp.class, lower("%damage% >= %actor.health%", d));
        assertFalse(d.hasErrors());
        assertEquals(new NumExpr.Var(2), cmp.left());
        assertEquals(Cmp.GE, cmp.op());
        assertEquals(new NumExpr.Var(1), cmp.right());
    }

    @Test
    void booleanVariableStandsAloneAsAGate() {
        Diagnostics d = new Diagnostics();
        assertEquals(new Cond.BoolVar(0), lower("%sneaking%", d));
        assertFalse(d.hasErrors());
    }

    @Test
    void notAndAndCombinators() {
        Diagnostics d = new Diagnostics();
        Cond c = lower("%sneaking% && !%blocking%", d);
        assertFalse(d.hasErrors());
        Cond.And and = assertInstanceOf(Cond.And.class, c);
        assertEquals(new Cond.BoolVar(0), and.left());
        assertEquals(new Cond.Not(new Cond.BoolVar(1)), and.right());
    }

    @Test
    void booleanEqualityComparison() {
        Diagnostics d = new Diagnostics();
        Cond.BoolCmp cmp = assertInstanceOf(Cond.BoolCmp.class, lower("%sneaking% == true", d));
        assertFalse(d.hasErrors());
        assertEquals(new Cond.BoolVar(0), cmp.left());
        assertTrue(cmp.equal());
        assertEquals(new Cond.BoolLit(true), cmp.right());
    }

    @Test
    void stringEquality() {
        Diagnostics d = new Diagnostics();
        Cond.StrCmp cmp = assertInstanceOf(Cond.StrCmp.class, lower("%name% == \"steve\"", d));
        assertFalse(d.hasErrors());
        assertEquals(new StrExpr.Var(0), cmp.left());
        assertTrue(cmp.equal());
        assertEquals(new StrExpr.Lit("steve"), cmp.right());
    }

    @Test
    void unknownVariableBecomesPapiInNumericCompare() {
        Diagnostics d = new Diagnostics();
        Cond.NumCmp cmp = assertInstanceOf(Cond.NumCmp.class, lower("%player_level% > 10", d));
        assertFalse(d.hasErrors());
        assertEquals(new NumExpr.Papi("player_level"), cmp.left());
        assertEquals(new NumExpr.Lit(10.0), cmp.right());
    }

    @Test
    void papiComparedToBooleanCoercesToBoolean() {
        Diagnostics d = new Diagnostics();
        // the common %essentials_afk% == true idiom: placeholder coerces to boolean
        Cond.BoolCmp cmp = assertInstanceOf(Cond.BoolCmp.class, lower("%essentials_afk% == true", d));
        assertFalse(d.hasErrors());
        assertEquals(new Cond.BoolPapi("essentials_afk"), cmp.left());
        assertTrue(cmp.equal());
        assertEquals(new Cond.BoolLit(true), cmp.right());
    }

    @Test
    void papiVersusBooleanVariableCoercesToBoolean() {
        Diagnostics d = new Diagnostics();
        Cond.BoolCmp cmp = assertInstanceOf(Cond.BoolCmp.class, lower("%afk% != %sneaking%", d));
        assertFalse(d.hasErrors());
        assertEquals(new Cond.BoolPapi("afk"), cmp.left());
        assertFalse(cmp.equal());
        assertEquals(new Cond.BoolVar(0), cmp.right());
    }

    @Test
    void papiVersusPapiIsStringEquality() {
        Diagnostics d = new Diagnostics();
        Cond.StrCmp cmp = assertInstanceOf(Cond.StrCmp.class, lower("%a_b% == %c_d%", d));
        assertFalse(d.hasErrors());
        assertEquals(new StrExpr.Papi("a_b"), cmp.left());
        assertEquals(new StrExpr.Papi("c_d"), cmp.right());
    }

    @Test
    void operatorPrecedenceIsPreservedThroughLowering() {
        Diagnostics d = new Diagnostics();
        // && binds tighter than ||  →  Or(cmp, And(cmp, cmp))
        Cond c = lower("%damage% == 1 || %actor.health% == 2 && %victim.health% == 3", d);
        assertFalse(d.hasErrors());
        Cond.Or or = assertInstanceOf(Cond.Or.class, c);
        assertInstanceOf(Cond.NumCmp.class, or.left());
        assertInstanceOf(Cond.And.class, or.right());
    }

    /** Every {@link ExprFn} maps to its compile kind — a missing arm would silently mis-lower a function. */
    @ParameterizedTest
    @EnumSource(ExprFn.class)
    void everyFunctionLowersToItsCompileKind(ExprFn fn) {
        Diagnostics d = new Diagnostics();
        StringBuilder args = new StringBuilder("%damage%");
        for (int i = 1; i < fn.arity(); i++) {
            args.append(", ").append(i);
        }
        Cond.NumCmp cmp = assertInstanceOf(Cond.NumCmp.class,
                lower(fn.token() + "(" + args + ") > 1", d));
        assertFalse(d.hasErrors());
        NumExpr.Fn lowered = assertInstanceOf(NumExpr.Fn.class, cmp.left());
        assertEquals(fn.name(), lowered.kind().name());
        assertEquals(fn.arity(), lowered.args().size());
        assertEquals(new NumExpr.Var(2), lowered.args().get(0)); // %damage% resolved to its dense slot
    }

    @Test
    void functionArgumentsLowerRecursivelyAndNest() {
        Diagnostics d = new Diagnostics();
        Cond.NumCmp cmp = assertInstanceOf(Cond.NumCmp.class,
                lower("clamp(min(%damage%, 2) + 1, 0, max(3, %actor.health%)) > 0", d));
        assertFalse(d.hasErrors());
        NumExpr.Fn clamp = assertInstanceOf(NumExpr.Fn.class, cmp.left());
        NumExpr.Bin sum = assertInstanceOf(NumExpr.Bin.class, clamp.args().get(0));
        assertEquals(NumExpr.FnKind.MIN, assertInstanceOf(NumExpr.Fn.class, sum.left()).kind());
        assertEquals(NumExpr.FnKind.MAX, assertInstanceOf(NumExpr.Fn.class, clamp.args().get(2)).kind());
    }

    // ── TARGET_VAR reads: %victim.var.<name>% is recognised by PREFIX at compile time and lowers to a
    // victim-scoped dynamic read, so it never falls through to the PlaceholderAPI passthrough.

    @Test
    void victimVarPrefixLowersToAVictimScopedRead() {
        Diagnostics d = new Diagnostics();
        Cond.NumCmp cmp = assertInstanceOf(Cond.NumCmp.class, lower("%victim.var.bleedstacks% >= 3", d));
        assertFalse(d.hasErrors(), () -> d.all().toString());
        NumExpr.EntityVar read = assertInstanceOf(NumExpr.EntityVar.class, cmp.left());
        assertEquals("bleedstacks", read.name());
    }

    @Test
    void victimVarNamesAreCaseInsensitiveAndKeepInnerDots() {
        Diagnostics d = new Diagnostics();
        Cond.NumCmp cmp = assertInstanceOf(Cond.NumCmp.class, lower("%victim.var.Mark.Beast% > 0", d));
        assertFalse(d.hasErrors(), () -> d.all().toString());
        // The store canonicalises case; the compiler must hand it the whole remainder, dots and all.
        assertEquals("Mark.Beast", assertInstanceOf(NumExpr.EntityVar.class, cmp.left()).name());
    }

    @Test
    void aBareVictimVarIsNotSilentlyAPlaceholder() {
        // Without the prefix arm this would lower to NumExpr.Papi and read null forever — a silent no-op.
        Diagnostics d = new Diagnostics();
        Cond.NumCmp cmp = assertInstanceOf(Cond.NumCmp.class, lower("%victim.var.x% == 0", d));
        assertFalse(cmp.left() instanceof NumExpr.Papi, "a victim var must not fall through to PAPI");
    }

    // ── Keyed potion families: %actor.potion.<effect>% / %victim.potion.<effect>% resolve their effect token
    // to an interned handle AT COMPILE TIME through the same §9 facade the resolve stage uses, so the runtime
    // never sees a potion name and never touches a constant that was renamed in the range.

    /** A resolver that knows exactly one potion effect, interned as 7 — the test owns both ends. */
    private static final PlatformResolvers ONE_POTION = new PlatformResolvers() {
        @Override public OptionalInt material(String token) { return OptionalInt.empty(); }
        @Override public OptionalInt sound(String token) { return OptionalInt.empty(); }
        @Override public OptionalInt potionEffect(String token) {
            return "SPEED".equals(token) ? OptionalInt.of(7) : OptionalInt.empty();
        }
        @Override public OptionalInt particle(String token) { return OptionalInt.empty(); }
        @Override public OptionalInt enchantment(String token) { return OptionalInt.empty(); }
        @Override public OptionalInt entityType(String token) { return OptionalInt.empty(); }
        @Override public OptionalInt attribute(String token) { return OptionalInt.empty(); }
    };

    private static Cond lowerResolving(String expr, Diagnostics d) {
        Expr ast = ExprParser.parse(expr, SRC, d).orElseThrow();
        return new ConditionCompiler(VARS, ONE_POTION).compile(ast, d).orElseThrow();
    }

    @ValueSource(strings = {"%actor.potion.SPEED% > 0", "%victim.potion.SPEED% > 0"})
    @ParameterizedTest
    void bothPotionScopesLowerToAResolvedHandle(String expr) {
        Diagnostics d = new Diagnostics();
        Cond.NumCmp cmp = assertInstanceOf(Cond.NumCmp.class, lowerResolving(expr, d));
        assertFalse(d.hasErrors(), () -> d.all().toString());
        NumExpr.PotionLevel read = assertInstanceOf(NumExpr.PotionLevel.class, cmp.left());
        assertEquals(7, read.handleId(), "the token resolved at compile, so no name reaches the runtime");
        assertEquals(expr.startsWith("%victim") ? NumExpr.Scope.VICTIM : NumExpr.Scope.ACTOR, read.scope());
    }

    @Test
    void aPotionTokenIsNotSilentlyAPlaceholder() {
        // Without the prefix arm this lowers to NumExpr.Papi and reads null forever — a silent no-op.
        Diagnostics d = new Diagnostics();
        Cond.NumCmp cmp = assertInstanceOf(Cond.NumCmp.class, lowerResolving("%actor.potion.SPEED% == 2", d));
        assertFalse(cmp.left() instanceof NumExpr.Papi, "a potion read must not fall through to PAPI");
    }

    @Test
    void anEffectUnknownOnThisVersionIsADiagnosticNotAPassthrough() {
        Diagnostics d = new Diagnostics();
        Expr ast = ExprParser.parse("%actor.potion.NOSUCHEFFECT% > 0", SRC, d).orElseThrow();
        assertTrue(new ConditionCompiler(VARS, ONE_POTION).compile(ast, d).isEmpty());
        assertTrue(d.all().stream().anyMatch(x -> x.is(DiagCode.E_UNKNOWN_HANDLE)));
    }

    @Test
    void aScopeThatNamesNeitherEntityKeepsItsPlaceholderFallthrough() {
        // %world.potion.X% names no activation entity, so the prefix arm must not claim it.
        Diagnostics d = new Diagnostics();
        Cond.NumCmp cmp = assertInstanceOf(Cond.NumCmp.class, lowerResolving("%world.potion.SPEED% > 0", d));
        assertInstanceOf(NumExpr.Papi.class, cmp.left());
    }

    @Test
    void aStringArgumentToAFunctionIsATypeError() {
        // Functions are numeric-only; the fault must be a diagnostic, never a lowering exception.
        Diagnostics d = lowerExpectingError("min(%name%, 2) > 1");
        assertTrue(d.all().stream().anyMatch(x -> x.is(DiagCode.E_COND_TYPE)));
    }

    @Test
    void aBareFunctionIsNotAConditionOnItsOwn() {
        Diagnostics d = lowerExpectingError("min(1, 2)");
        assertTrue(d.all().stream().anyMatch(x -> x.is(DiagCode.E_COND_TYPE)));
    }
}
