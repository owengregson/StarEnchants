package schema.grammar.expr;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
 * Malformed inputs must (1) never throw, (2) recover with a best-effort non-null tree, and (3) report a
 * specific parse-family {@link DiagCode} — asserted by CODE, not by English message wording (the
 * E_PARSE_* split is what makes that possible). docs/architecture.md §7, §10.
 */
class ExprParserErrorTest {

    private static final Source SRC = Source.of("c.yml", 3, 1);

    private static Result parse(String text) {
        Diagnostics diags = new Diagnostics();
        Optional<Expr> e = assertDoesNotThrow(() -> ExprParser.parse(text, SRC, diags));
        return new Result(e, diags);
    }

    private record Result(Optional<Expr> tree, Diagnostics diags) {
        boolean reported(DiagCode code) {
            return diags.all().stream().anyMatch(d -> d.is(code));
        }
    }

    /** Each malformed input paired with the specific fault that characterizes it. */
    private static Stream<Arguments> malformed() {
        return Stream.of(
                arguments("%a% &&", DiagCode.E_PARSE_EXPECTED_VALUE),   // binary op, no right operand
                arguments("%a% ||", DiagCode.E_PARSE_EXPECTED_VALUE),
                arguments("&& %a%", DiagCode.E_PARSE_EXPECTED_VALUE),   // leading binary op, no left operand
                arguments("%a% < ", DiagCode.E_PARSE_EXPECTED_VALUE),   // comparator, no right operand
                arguments("< %a%", DiagCode.E_PARSE_EXPECTED_VALUE),    // leading comparator
                arguments("!", DiagCode.E_PARSE_EXPECTED_VALUE),        // bang, no operand
                arguments("()", DiagCode.E_PARSE_EXPECTED_VALUE),       // empty group
                arguments("%a% == == %b%", DiagCode.E_PARSE_EXPECTED_VALUE), // doubled comparator
                arguments("(%a%", DiagCode.E_PARSE_UNCLOSED_GROUP),
                arguments("(%a% || %b%", DiagCode.E_PARSE_UNCLOSED_GROUP),
                arguments("%a%)", DiagCode.E_PARSE_TRAILING),           // stray closing paren = trailing token
                arguments("%a% %b%", DiagCode.E_PARSE_TRAILING),        // two values, no operator
                arguments("1 2", DiagCode.E_PARSE_TRAILING),
                arguments("%a% < 1 < 2", DiagCode.E_PARSE_CHAINED_CMP),
                arguments("@", DiagCode.E_PARSE_BAD_CHAR));             // lexer fault surfaces in the same stream
    }

    @ParameterizedTest
    @MethodSource("malformed")
    void malformedInputRecoversAndReportsItsSpecificCode(String input, DiagCode expected) {
        Result r = parse(input);
        assertTrue(r.reported(expected),
                () -> "expected " + expected + " for <" + input + ">, got " + r.diags().all());
        // Recovery always yields a usable, non-null tree.
        assertTrue(r.tree().isPresent(), () -> "expected a recovery tree for <" + input + ">");
        assertNotNull(r.tree().get());
    }

    @Test
    void blankInputYieldsNoTreeAndNoError() {
        Result r = parse("");
        assertTrue(r.tree().isEmpty());
        assertTrue(r.diags().isEmpty());
    }

    @Test
    void missingRightOperandIsReportedAtEnd() {
        Result r = parse("%a% &&");
        // '&&' at col 5..6; the missing operand surfaces at EOF (col 7).
        Diagnostic d = r.diags().all().get(0);
        assertTrue(d.is(DiagCode.E_PARSE_EXPECTED_VALUE));
        assertEquals(3, d.source().line());
        assertEquals(7, d.source().col());
    }

    @Test
    void strayClosingParenIsReportedAtItsColumn() {
        Result r = parse("%a%)");
        Diagnostic d = r.diags().all().get(0);
        assertTrue(d.is(DiagCode.E_PARSE_TRAILING));
        assertEquals(4, d.source().col()); // ')' at index 3 -> col 4
    }

    @Test
    void oneFaultProducesExactlyOneFinding() {
        // A single missing operand is ONE finding, not an avalanche.
        Result r = parse("!");
        assertEquals(1, r.diags().all().size(), () -> r.diags().all().toString());
    }

    @Test
    void deeplyMalformedTerminatesWithoutThrowing() {
        // Pathological input: operators only. Must terminate, recover, and report a parse fault.
        Result r = parse("&& || < <= > >= == != !");
        assertTrue(r.diags().hasErrors());
        assertNotNull(r.tree().orElse(null));
    }
}
