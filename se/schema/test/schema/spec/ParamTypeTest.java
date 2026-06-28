package schema.spec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import schema.diag.DiagCode;
import schema.diag.Diagnostics;
import schema.diag.Source;
import schema.grammar.expr.Expr;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ParamTypeTest {

    private static final Source SRC = Source.of("test.yml", 1, 1);

    private static double dbl(Optional<Object> v) {
        return ((Number) v.orElseThrow()).doubleValue();
    }

    @Test
    void doubleParsesValidValue() {
        Diagnostics d = new Diagnostics();
        assertEquals(2.5, dbl(D.DOUBLE.parse("2.5", SRC, d)));
        assertFalse(d.hasErrors());
    }

    @Test
    void doubleRejectsNonNumberWithTypeError() {
        Diagnostics d = new Diagnostics();
        assertTrue(D.DOUBLE.parse("abc", SRC, d).isEmpty());
        assertTrue(d.hasErrors());
        assertTrue(d.all().get(0).is(DiagCode.E_TYPE), () -> d.all().toString());
    }

    @Test
    void doubleEnforcesRange() {
        Diagnostics over = new Diagnostics();
        assertTrue(D.DOUBLE.min(0).max(100).parse("150", SRC, over).isEmpty());
        assertTrue(over.all().get(0).is(DiagCode.E_RANGE), () -> over.all().toString());

        Diagnostics under = new Diagnostics();
        assertTrue(D.DOUBLE.min(0).max(100).parse("-1", SRC, under).isEmpty());
        assertTrue(under.all().get(0).is(DiagCode.E_RANGE), () -> under.all().toString());

        Diagnostics ok = new Diagnostics();
        assertEquals(0.0, dbl(D.DOUBLE.min(0).max(100).parse("0", SRC, ok)));
        assertFalse(ok.hasErrors());
    }

    @Test
    void intRejectsDecimals() {
        Diagnostics d = new Diagnostics();
        assertTrue(D.INT.parse("3.5", SRC, d).isEmpty());
        assertTrue(d.all().get(0).is(DiagCode.E_TYPE), () -> d.all().toString());

        Diagnostics ok = new Diagnostics();
        assertEquals(3L, D.INT.parse("3", SRC, ok).orElseThrow());
        assertFalse(ok.hasErrors());
    }

    @Test
    void ticksAreFlooredAtZero() {
        Diagnostics d = new Diagnostics();
        assertTrue(D.TICKS.parse("-1", SRC, d).isEmpty());
        assertTrue(d.all().get(0).is(DiagCode.E_RANGE), () -> d.all().toString());
    }

    @Test
    void boolAcceptsAliasesAndRejectsOthers() {
        for (String t : new String[] {"true", "yes", "on", "1"}) {
            assertEquals(Boolean.TRUE, D.BOOL.parse(t, SRC, new Diagnostics()).orElseThrow(), t);
        }
        for (String f : new String[] {"false", "no", "off", "0"}) {
            assertEquals(Boolean.FALSE, D.BOOL.parse(f, SRC, new Diagnostics()).orElseThrow(), f);
        }
        Diagnostics d = new Diagnostics();
        assertTrue(D.BOOL.parse("maybe", SRC, d).isEmpty());
        assertTrue(d.all().get(0).is(DiagCode.E_TYPE), () -> d.all().toString());
    }

    @Test
    void enumNormalizesToCanonicalSpelling() {
        ParamType shape = D.enumOf("CIRCLE", "SQUARE");
        assertEquals("CIRCLE", shape.parse("circle", SRC, new Diagnostics()).orElseThrow());

        Diagnostics d = new Diagnostics();
        assertTrue(shape.parse("triangle", SRC, d).isEmpty());
        assertTrue(d.all().get(0).is(DiagCode.E_ENUM), () -> d.all().toString());
    }

    @Test
    void defaultMakesArgumentOptional() {
        ParamType t = D.INT.def(0);
        assertFalse(t.isRequired());
        assertEquals("0", t.defaultRaw().orElseThrow());
        assertTrue(D.INT.isRequired());
    }

    @Test
    void labelRendersTypeAndBounds() {
        assertEquals("double[0..100]", D.DOUBLE.min(0).max(100).label());
        assertEquals("double[0..]", D.DOUBLE.min(0).label());
        assertEquals("int", D.INT.label());
        assertEquals("enum{CIRCLE|SQUARE}", D.enumOf("CIRCLE", "SQUARE").label());
    }

    @Test
    void completionsCoverEnumsAndBooleans() {
        assertEquals(java.util.List.of("CIRCLE"), D.enumOf("CIRCLE", "SQUARE").completions("ci"));
        assertEquals(java.util.List.of("true"), D.BOOL.completions("t"));
        assertTrue(D.DOUBLE.completions("").isEmpty());
    }

    // ── HANDLE: a version-volatile referent. parse() keeps the token verbatim (resolve interns it later
    // and warns-and-skips unknowns, §9); only an empty token is a parse error here.

    @Test
    void handleKeepsTheTokenVerbatimAndTrimmed() {
        Diagnostics d = new Diagnostics();
        assertEquals("DIAMOND_SWORD", D.material().parse("DIAMOND_SWORD", SRC, d).orElseThrow());
        assertEquals("ENTITY_GENERIC_HURT", D.sound().parse("  ENTITY_GENERIC_HURT  ", SRC, d).orElseThrow());
        assertFalse(d.hasErrors()); // an unknown name is resolve's concern, not a parse error
    }

    @Test
    void handleRejectsAnEmptyToken() {
        Diagnostics d = new Diagnostics();
        assertTrue(D.material().parse("   ", SRC, d).isEmpty());
        assertTrue(d.all().get(0).is(DiagCode.E_TYPE), () -> d.all().toString());
    }

    @Test
    void handleLabelAndCategoryComeFromTheCategory() {
        assertEquals("material", D.material().label());
        assertEquals("potion_effect", D.potionEffect().label());
        assertEquals("enchantment", D.enchantment().label());
        assertEquals(HandleCategory.MATERIAL, D.material().handleCategory());
        assertEquals(HandleCategory.PARTICLE, D.particle().handleCategory());
    }

    // ── A %var%/arithmetic token is a valid numeric argument: it parses to an Expr AST evaluated per
    // activation, and its range is deliberately NOT checked statically (the author owns the value, §3.4).

    @Test
    void numericArgumentAcceptsAVariableExpression() {
        Diagnostics d = new Diagnostics();
        Object v = D.DOUBLE.parse("%combo%", SRC, d).orElseThrow();
        assertInstanceOf(Expr.VarRef.class, v);
        assertFalse(d.hasErrors());
    }

    @Test
    void numericArgumentAcceptsArithmeticForDoubleAndInt() {
        Diagnostics d = new Diagnostics();
        assertInstanceOf(Expr.Arith.class, D.DOUBLE.parse("%combo% * 10", SRC, d).orElseThrow());
        // INT admits an expression too — its value is narrowed to a whole number at read time.
        assertInstanceOf(Expr.Arith.class, D.INT.parse("%level% + 1", SRC, d).orElseThrow());
        assertFalse(d.hasErrors());
    }

    @Test
    void rangeIsNotCheckedOnAnExpressionArgument() {
        // %combo% * 1000 could exceed the [0..100] bound, but a bound can't be checked on an expression.
        Diagnostics d = new Diagnostics();
        assertInstanceOf(Expr.class, D.DOUBLE.min(0).max(100).parse("%combo% * 1000", SRC, d).orElseThrow());
        assertFalse(d.hasErrors());
    }
}
