package schema.grammar.expr;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import schema.diag.Diagnostic;
import schema.diag.Diagnostics;
import schema.diag.Source;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Malformed inputs must produce an {@code E_PARSE} {@link Diagnostic} and recover
 * with a best-effort, non-null tree — never throw to the caller
 * (docs/architecture.md §7, §10; the diagnostics philosophy of se-schema).
 */
class ExprParserErrorTest {

    private static final Source SRC = Source.of("c.yml", 3, 1);

    private static Result parse(String text) {
        Diagnostics diags = new Diagnostics();
        Optional<Expr> e = assertDoesNotThrow(() -> ExprParser.parse(text, SRC, diags));
        return new Result(e, diags);
    }

    private record Result(Optional<Expr> tree, Diagnostics diags) {
        boolean hasParseError() {
            return diags.all().stream().anyMatch(d -> d.code().equals("E_PARSE"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "%a% &&",            // binary operator with no right operand
            "%a% ||",            // ditto
            "&& %a%",            // leading binary operator, no left operand
            "%a% < ",            // comparator with no right operand
            "< %a%",             // leading comparator
            "(%a%",              // unterminated group
            "(%a% || %b%",       // unterminated nested group
            "%a%)",              // stray closing paren
            "!",                 // bang with no operand
            "%a% < 1 < 2",       // chained comparators (non-associative)
            "%a% %b%",           // two values, no operator between them
            "1 2",               // two numbers, no operator
            "()",                // empty group
            "@",                 // stray character (lexer error surfaces as E_PARSE)
            "%a% == == %b%",     // doubled comparator
            "",                  // blank (no tree, but must not throw)
    })
    void malformedInputsNeverThrowAndReportParseError(String input) {
        Result r = parse(input);
        if (input.isBlank()) {
            assertTrue(r.tree().isEmpty(), "blank input yields no tree");
            return;
        }
        assertTrue(r.hasParseError(), () -> "expected E_PARSE for <" + input + ">, got " + r.diags().all());
        // Recovery always yields a usable, non-null tree (never null, never empty here).
        assertTrue(r.tree().isPresent(), () -> "expected a recovery tree for <" + input + ">");
        assertNotNull(r.tree().get());
    }

    @Test
    void chainedComparatorMessageIsHelpful() {
        Result r = parse("%a% < 1 < 2");
        Diagnostic d = r.diags().all().get(0);
        assertEquals("E_PARSE", d.code());
        assertTrue(d.message().toLowerCase().contains("chain"),
                () -> "message should mention chaining: " + d.message());
    }

    @Test
    void missingRightOperandPointsAtEnd() {
        Result r = parse("%a% &&");
        assertTrue(r.hasParseError());
        Diagnostic d = r.diags().all().get(0);
        // '&&' at col 5..6; the missing operand is reported at EOF (col 7).
        assertEquals(3, d.source().line());
        assertEquals(7, d.source().col());
    }

    @Test
    void trailingTokenAfterCompleteExpressionIsReported() {
        Result r = parse("%a% %b%");
        assertTrue(r.hasParseError());
        Diagnostic d = r.diags().all().get(0);
        assertTrue(d.message().contains("after the expression"), d::message);
    }

    @Test
    void unterminatedGroupReportsMissingParen() {
        Result r = parse("(%a% || %b%");
        assertTrue(r.diags().all().stream()
                .anyMatch(d -> d.message().contains("closing ')'")), () -> r.diags().all().toString());
    }

    @Test
    void strayClosingParenIsReportedAtItsColumn() {
        Result r = parse("%a%)");
        assertTrue(r.hasParseError());
        // ')' at index 3 -> col 4
        assertTrue(r.diags().all().stream().anyMatch(d -> d.source().col() == 4),
                () -> r.diags().all().toString());
    }

    @Test
    void recoveryDoesNotCascadeIntoManyErrorsForOneFault() {
        // A single missing operand should be ONE finding, not an avalanche.
        Result r = parse("!");
        assertEquals(1, r.diags().all().size(), () -> r.diags().all().toString());
    }

    @Test
    void emptyGroupReportsExpectedValue() {
        Result r = parse("()");
        assertTrue(r.hasParseError());
        assertTrue(r.diags().all().stream().anyMatch(d -> d.message().contains("expected a value")),
                () -> r.diags().all().toString());
    }

    @Test
    void deeplyMalformedDoesNotHangOrThrow() {
        // Pathological input: operators only. Must terminate and not throw.
        Result r = parse("&& || < <= > >= == != !");
        assertTrue(r.hasParseError());
        assertNotNull(r.tree().orElse(null));
    }
}
