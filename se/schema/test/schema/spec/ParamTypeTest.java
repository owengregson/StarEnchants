package schema.spec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import schema.diag.DiagCode;
import schema.diag.Diagnostics;
import schema.diag.Source;
import schema.grammar.expr.Expr;
import schema.grammar.expr.ExprFn;
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
    void composedEnumAcceptsAConjunctionAndStillRejectsABadPart() {
        ParamType shape = D.enumSetOf("CIRCLE", "SQUARE");
        // Each part normalises independently, in authored order — the runtime splits on '+' and re-reads them.
        assertEquals("SQUARE+CIRCLE", shape.parse("square+circle", SRC, new Diagnostics()).orElseThrow());
        assertEquals("CIRCLE", shape.parse("circle", SRC, new Diagnostics()).orElseThrow());

        Diagnostics d = new Diagnostics();
        assertTrue(shape.parse("circle+triangle", SRC, d).isEmpty());
        assertTrue(d.all().get(0).is(DiagCode.E_ENUM), () -> d.all().toString());

        // A plain enum must NOT quietly admit the composed form.
        Diagnostics plain = new Diagnostics();
        assertTrue(D.enumOf("CIRCLE", "SQUARE").parse("circle+square", SRC, plain).isEmpty());
        assertTrue(plain.all().get(0).is(DiagCode.E_ENUM), () -> plain.all().toString());
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
        assertEquals("enum set{CIRCLE|SQUARE}", D.enumSetOf("CIRCLE", "SQUARE").label());
    }

    @Test
    void completionsCoverEnumsAndBooleans() {
        assertEquals(java.util.List.of("CIRCLE"), D.enumOf("CIRCLE", "SQUARE").completions("ci"));
        assertEquals(java.util.List.of("true"), D.BOOL.completions("t"));
        assertTrue(D.DOUBLE.completions("").isEmpty());
        // A composed enum completes the part after the last '+', keeping what is already typed.
        assertEquals(java.util.List.of("CIRCLE+SQUARE"), D.enumSetOf("CIRCLE", "SQUARE").completions("CIRCLE+sq"));
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
    void handleSetStripsTheGroupingBracketsOrQuotes() {
        Diagnostics d = new Diagnostics();
        // A selector body is comma-split, so a multi-entry set has to be bracketed to survive the lexer; the
        // brackets are grouping and must not reach resolve, which would then look up a material named "[STONE".
        assertEquals("STONE,DIRT", D.materials().parse("[STONE,DIRT]", SRC, d).orElseThrow());
        assertEquals("STONE,DIRT", D.materials().parse("\"STONE,DIRT\"", SRC, d).orElseThrow());
        assertEquals("STONE", D.materials().parse("STONE", SRC, d).orElseThrow());
        assertEquals("", D.materials().parse("[]", SRC, d).orElseThrow()); // an empty SET is a value, not a fault
        assertFalse(d.hasErrors());
    }

    @Test
    void exprMapParsesEachBindingToItsOwnExpression() {
        Diagnostics d = new Diagnostics();
        ExprMap map = (ExprMap) D.exprMap().parse("souls=%actor.souls%; doubled=%kills% * 2", SRC, d).orElseThrow();
        assertFalse(d.hasErrors());
        assertEquals(java.util.List.of("souls", "doubled"), java.util.List.copyOf(map.entries().keySet()),
                "authored order is kept — the bindings render in the order they were written");
        assertInstanceOf(Expr.VarRef.class, map.entries().get("souls"));
        assertInstanceOf(Expr.Arith.class, map.entries().get("doubled"));
    }

    @Test
    void exprMapAcceptsNoBindingsAndStripsGroupingBrackets() {
        Diagnostics d = new Diagnostics();
        // Empty is a value, not a fault (the handle-set rule); brackets are grouping, so a binding set can
        // survive the comma-splitting selector lexer the same way a material set does.
        assertTrue(((ExprMap) D.exprMap().parse("", SRC, d).orElseThrow()).isEmpty());
        assertEquals(1, ((ExprMap) D.exprMap().parse("[a=1]", SRC, d).orElseThrow()).entries().size());
        assertFalse(d.hasErrors());
    }

    @Test
    void exprMapRejectsAnEntryThatIsNotANameEqualsExpressionBinding() {
        Diagnostics d = new Diagnostics();
        assertTrue(D.exprMap().parse("%actor.souls%", SRC, d).isEmpty(), "a bare expression names nothing");
        assertTrue(d.all().stream().anyMatch(x -> x.is(DiagCode.E_TYPE)));
    }

    @Test
    void exprMapBindingsAreClampedToTheDeclaredRangeLikeAScalarExpression() {
        Diagnostics d = new Diagnostics();
        // The §3.4 range rule reaches INTO the map: a declared bound is a runtime guarantee for every
        // binding, not only for a scalar expression argument.
        ExprMap map = (ExprMap) D.exprMap().min(0).max(10).parse("v=%kills%", SRC, d).orElseThrow();
        assertFalse(d.hasErrors());
        assertEquals(ExprFn.CLAMP, assertInstanceOf(Expr.Call.class, map.entries().get("v")).fn());
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
    void rangeIsNotCheckedOnAnExpressionArgumentButIsAppliedAsAClamp() {
        // A bound can't be checked on an expression, so the value is confined at evaluation instead: the
        // parsed tree comes back wrapped in the spec's own range, without the author restating the bound.
        Diagnostics d = new Diagnostics();
        Expr.Call call = assertInstanceOf(Expr.Call.class,
                D.DOUBLE.min(0).max(100).parse("%combo% * 1000", SRC, d).orElseThrow());
        assertFalse(d.hasErrors());
        assertEquals(ExprFn.CLAMP, call.fn());
        assertInstanceOf(Expr.Arith.class, call.args().get(0));
        assertEquals("0.0", assertInstanceOf(Expr.NumberLit.class, call.args().get(1)).raw());
        assertEquals("100.0", assertInstanceOf(Expr.NumberLit.class, call.args().get(2)).raw());
    }

    @Test
    void aConstantOutOfRangeIsStillAHardError() {
        // The clamp is for expressions only — a constant the author can see is wrong stays a diagnostic.
        Diagnostics d = new Diagnostics();
        assertTrue(D.DOUBLE.min(0).max(100).parse("250", SRC, d).isEmpty());
        assertTrue(d.all().stream().anyMatch(x -> x.is(DiagCode.E_RANGE)));
    }

    @Test
    void anUnboundedParamLeavesTheExpressionUnwrapped() {
        // No declared bound = nothing to confine; the fast path must not grow a pointless clamp node.
        Diagnostics d = new Diagnostics();
        assertInstanceOf(Expr.Arith.class, D.DOUBLE.parse("%combo% * 1000", SRC, d).orElseThrow());
        assertFalse(d.hasErrors());
    }

    @Test
    void aHalfBoundedParamClampsOnlyTheDeclaredSide() {
        Diagnostics d = new Diagnostics();
        Expr.Call call = assertInstanceOf(Expr.Call.class, D.DOUBLE.min(0).parse("%combo% - 5", SRC, d).orElseThrow());
        assertEquals(ExprFn.CLAMP, call.fn());
        assertEquals("0.0", assertInstanceOf(Expr.NumberLit.class, call.args().get(1)).raw());
        assertEquals("Infinity", assertInstanceOf(Expr.NumberLit.class, call.args().get(2)).raw());
        assertFalse(d.hasErrors());
    }

    @Test
    void aFunctionCallIsRecognisedAsAnExpressionArgument() {
        // min(...) carries none of the % * / + - markers, so the expression sniff must key on the call itself.
        Diagnostics d = new Diagnostics();
        assertInstanceOf(Expr.Call.class, D.DOUBLE.parse("min(50, 20)", SRC, d).orElseThrow());
        assertInstanceOf(Expr.Call.class, D.INT.parse("floor(7)", SRC, d).orElseThrow());
        assertFalse(d.hasErrors());
    }

    @Test
    void conditionParsesToABooleanExpressionTreeAndIsNeverClamped() {
        // ADR-0076: a CONDITION arg is a GATE, so it must reach the lower stage as the comparison the author
        // wrote — never wrapped in the synthetic clamp a numeric argument gets, which would make it a number.
        Diagnostics d = new Diagnostics();
        assertInstanceOf(Expr.Compare.class, D.CONDITION.parse("%target.roll% < 25", SRC, d).orElseThrow());
        assertFalse(d.hasErrors());
        assertEquals("condition", D.CONDITION.label());
    }

    @Test
    void conditionReportsASyntaxFaultInsteadOfSilentlyKeepingEverybody() {
        // The parser recovers rather than returning empty, so what matters is that the fault BLOCKS: a
        // silently-recovered each-if would keep every target and read as "the filter does nothing".
        Diagnostics d = new Diagnostics();
        D.CONDITION.parse("(%target.roll% < 25", SRC, d);
        assertTrue(d.hasErrors(), () -> d.all().toString());
        assertTrue(d.all().get(0).is(DiagCode.E_PARSE_UNCLOSED_GROUP), () -> d.all().toString());
    }

    @Test
    void perTargetAndHoistedAreDeclarationsThatSurviveEveryWither() {
        // Both are read by the executor (one picks the cursor-advancing iterable, the other keeps the knob out
        // of Args), so a later .min()/.max()/.optional() must not quietly drop them.
        ParamType declared = D.DOUBLE.perTarget().hoisted().min(0).max(100).optional();
        assertTrue(declared.isPerTarget());
        assertTrue(declared.isHoisted());
        assertFalse(declared.isRequired());
        assertFalse(D.DOUBLE.isPerTarget());
        assertFalse(D.DOUBLE.isHoisted());
    }
}
