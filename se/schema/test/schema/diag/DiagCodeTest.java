package schema.diag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Pins the wire-compatibility contract of {@link DiagCode}: every constant's {@link DiagCode#name()}
 * is exactly the string {@link Diagnostic#code()} carries, so migrating a producer from a raw
 * {@code "E_RANGE"} literal to {@code DiagCode.E_RANGE} cannot change the observed code. This is what
 * lets producers and tests both reference the enum instead of re-typing the literal in two places.
 */
class DiagCodeTest {

    @ParameterizedTest
    @EnumSource(DiagCode.class)
    void everyCodeCarriesItsNameAsTheWireString(DiagCode code) {
        // The wire code a producer emits is exactly the enum's name — the single-source contract.
        assertEquals(code.name(), Diagnostic.error(code, "m", Source.UNKNOWN).code());
        assertEquals(code.name(), code.code());
    }

    @Test
    void isMatchesOnlyItsOwnCode() {
        Diagnostic d = Diagnostic.error(DiagCode.E_RANGE, "out of range", Source.UNKNOWN);
        assertTrue(d.is(DiagCode.E_RANGE));
        assertFalse(d.is(DiagCode.E_PARSE));
        assertFalse(d.is(null));
    }

    @Test
    void isReadsTheRawCodeStringNotOnlyEnumBuiltDiagnostics() {
        // A diagnostic carrying a raw literal code (built via the canonical ctor, e.g. a test fixture with an
        // off-catalogue code) still answers is() — is() matches the wire string, not the enum identity.
        Diagnostic raw = new Diagnostic(Severity.ERROR, "E_DUPLICATE_KEY", "dup", Source.UNKNOWN, null);
        assertTrue(raw.is(DiagCode.E_DUPLICATE_KEY));
        assertFalse(raw.is(DiagCode.E_DUP_KEY)); // the intentionally-distinct near-twin
    }

    @Test
    void collectorDiagCodeOverloadsCollectWithTheRightSeverity() {
        Diagnostics diags = new Diagnostics()
                .warning(DiagCode.W_EXTRA_ARGS, "extra", Source.UNKNOWN)
                .error(DiagCode.E_TYPE, "bad type", Source.UNKNOWN);
        assertTrue(diags.hasErrors());
        assertEquals(1, diags.count(Severity.ERROR));
        assertEquals(1, diags.count(Severity.WARNING));
        assertTrue(diags.all().get(0).is(DiagCode.W_EXTRA_ARGS));
        assertTrue(diags.all().get(1).is(DiagCode.E_TYPE));
    }

    @Test
    void severityParameterizedAddRoutesBySeverity() {
        Diagnostics diags = new Diagnostics().add(Severity.WARNING, DiagCode.W_CONFIG_BOOL, "m", Source.UNKNOWN);
        assertEquals(1, diags.count(Severity.WARNING));
        assertFalse(diags.hasErrors());
        assertTrue(diags.all().get(0).is(DiagCode.W_CONFIG_BOOL));
    }
}
