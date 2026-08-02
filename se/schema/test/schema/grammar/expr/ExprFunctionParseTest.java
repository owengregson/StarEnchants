package schema.grammar.expr;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import schema.diag.DiagCode;
import schema.diag.Diagnostic;
import schema.diag.Diagnostics;
import schema.diag.Source;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The call production {@code name(arg, …)}: a function is legal wherever a numeric value is, its arity is
 * fixed per {@link ExprFn}, and a wrong arity / unknown name is ONE diagnostic at the name's column with
 * recovery (docs/architecture.md §3.4, §7).
 */
class ExprFunctionParseTest {

    private static final Source SRC = Source.of("c.yml", 3, 1);

    private static Result parse(String text) {
        Diagnostics diags = new Diagnostics();
        Optional<Expr> e = assertDoesNotThrow(() -> ExprParser.parse(text, SRC, diags));
        return new Result(e, diags);
    }

    private record Result(Optional<Expr> tree, Diagnostics diags) {
    }

    /** Every declared function, as a standalone numeric expression and inside a comparison. */
    private static Stream<Arguments> callForms() {
        return Stream.of(
                arguments("min(1, 2)", ExprFn.MIN),
                arguments("max(%damage%, 4)", ExprFn.MAX),
                arguments("clamp(%bleedstacks% * 0.5, 2, 10)", ExprFn.CLAMP),
                arguments("floor(%level% / 2)", ExprFn.FLOOR),
                arguments("rand(1, 3)", ExprFn.RAND));
    }

    @ParameterizedTest
    @MethodSource("callForms")
    void parsesStandaloneAsAParamExpression(String text, ExprFn fn) {
        Result r = parse(text);
        assertTrue(r.diags().isEmpty(), () -> r.diags().all().toString());
        Expr.Call call = assertInstanceOf(Expr.Call.class, r.tree().orElseThrow());
        assertEquals(fn, call.fn());
        assertEquals(fn.arity(), call.args().size());
    }

    @ParameterizedTest
    @MethodSource("callForms")
    void parsesAsAComparisonOperand(String text, ExprFn fn) {
        Result r = parse(text + " < 3");
        assertTrue(r.diags().isEmpty(), () -> r.diags().all().toString());
        Expr.Compare cmp = assertInstanceOf(Expr.Compare.class, r.tree().orElseThrow());
        assertEquals(fn, assertInstanceOf(Expr.Call.class, cmp.left()).fn());
    }

    @Test
    void callsNestInEachOtherAndInArithmetic() {
        Result r = parse("clamp(min(%a%, 2) + 1, 0, max(3, %b%))");
        assertTrue(r.diags().isEmpty(), () -> r.diags().all().toString());
        Expr.Call clamp = assertInstanceOf(Expr.Call.class, r.tree().orElseThrow());
        assertEquals(ExprFn.CLAMP, clamp.fn());
        Expr.Arith sum = assertInstanceOf(Expr.Arith.class, clamp.args().get(0));
        assertEquals(ExprFn.MIN, assertInstanceOf(Expr.Call.class, sum.left()).fn());
        assertEquals(ExprFn.MAX, assertInstanceOf(Expr.Call.class, clamp.args().get(2)).fn());
    }

    /** A malformed call, its characterizing code, and the 1-based column the fault is anchored at. */
    private static Stream<Arguments> malformedCalls() {
        return Stream.of(
                arguments("min(1)", DiagCode.E_PARSE_FN_ARITY, 1),
                arguments("clamp(1,2)", DiagCode.E_PARSE_FN_ARITY, 1),
                arguments("floor(1, 2)", DiagCode.E_PARSE_FN_ARITY, 1),
                arguments("%damage% > unknownfn(1)", DiagCode.E_PARSE_UNKNOWN_FN, 12));
    }

    @ParameterizedTest
    @MethodSource("malformedCalls")
    void aMalformedCallIsExactlyOneDiagnosticAtItsName(String input, DiagCode expected, int col) {
        Result r = parse(input);
        assertEquals(1, r.diags().all().size(), () -> r.diags().all().toString());
        Diagnostic d = r.diags().all().get(0);
        assertTrue(d.is(expected), () -> "expected " + expected + " for <" + input + ">, got " + d.code());
        assertEquals(3, d.source().line());
        assertEquals(col, d.source().col());
        assertTrue(r.tree().isPresent()); // warn-and-skip: parsing still yields a usable tree
    }

    @Test
    void anUnterminatedCallRecoversWithoutThrowing() {
        Result r = parse("min(1, 2");
        assertTrue(r.diags().hasErrors());
        assertTrue(r.tree().isPresent());
    }

    @Test
    void aFunctionNameWithoutParensStaysAVariableReference() {
        // Back-compat: bare identifiers are unscoped variable names, and `min` must not become reserved.
        Result r = parse("%a% == min");
        assertTrue(r.diags().isEmpty(), () -> r.diags().all().toString());
        Expr.Compare cmp = assertInstanceOf(Expr.Compare.class, r.tree().orElseThrow());
        assertEquals("min", assertInstanceOf(Expr.VarRef.class, cmp.right()).name());
    }
}
