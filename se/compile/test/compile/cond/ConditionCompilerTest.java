package compile.cond;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.model.cond.Cond;
import compile.model.cond.NumExpr;
import compile.model.cond.StrExpr;
import schema.diag.Diagnostics;
import schema.diag.Source;
import schema.grammar.expr.Cmp;
import schema.grammar.expr.Expr;
import schema.grammar.expr.ExprParser;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

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
        Cond c = lower("%name% contains \"a|b\"", d);
        assertFalse(d.hasErrors());
        Cond.StrContains sc = assertInstanceOf(Cond.StrContains.class, c);
        assertEquals(new StrExpr.Var(0), sc.left());
        assertEquals(new StrExpr.Lit("a|b"), sc.right());
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

    @Test
    void matchesRegexRejectsANonLiteralPattern() {
        Diagnostics d = lowerExpectingError("%name% matchesregex %name%");
        assertEquals("E_COND_TYPE", d.all().get(0).code());
    }

    @Test
    void matchesRegexRejectsAnInvalidPattern() {
        Diagnostics d = lowerExpectingError("%name% matchesregex \"[\"");
        assertEquals("E_COND_TYPE", d.all().get(0).code());
    }

    @Test
    void stringOperatorRejectsANumericOperand() {
        Diagnostics d = lowerExpectingError("%damage% contains \"x\""); // damage is numeric
        assertEquals("E_COND_TYPE", d.all().get(0).code());
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

    @Test
    void stringOrderingIsATypeError() {
        assertEquals("E_COND_TYPE", lowerExpectingError("%name% < \"x\"").all().get(0).code());
    }

    @Test
    void comparingNumberWithStringIsATypeError() {
        assertEquals("E_COND_TYPE", lowerExpectingError("%damage% == %name%").all().get(0).code());
    }

    @Test
    void bareNumberIsNotACondition() {
        assertEquals("E_COND_TYPE", lowerExpectingError("5").all().get(0).code());
    }

    @Test
    void bareNumericVariableIsNotACondition() {
        assertEquals("E_COND_TYPE", lowerExpectingError("%damage%").all().get(0).code());
    }

    @Test
    void barePlaceholderMustBeCompared() {
        assertEquals("E_COND_TYPE", lowerExpectingError("%some_papi%").all().get(0).code());
    }
}
