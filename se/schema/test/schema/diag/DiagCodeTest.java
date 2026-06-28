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
    void everyCodeRoundTripsThroughTheDiagCodeOverloadUnchanged(DiagCode code) {
        Diagnostic viaEnum = Diagnostic.error(code, "m", Source.UNKNOWN);
        Diagnostic viaString = Diagnostic.error(code.name(), "m", Source.UNKNOWN);
        // The enum overload is just a relabelling of the string overload: same wire code, both ways.
        assertEquals(viaString.code(), viaEnum.code());
        assertEquals(code.name(), viaEnum.code());
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
        // A diagnostic built the old way (raw literal) still answers is() — the migration can proceed
        // producer-by-producer without a flag day.
        Diagnostic legacy = Diagnostic.error("E_DUPLICATE_KEY", "dup", Source.UNKNOWN);
        assertTrue(legacy.is(DiagCode.E_DUPLICATE_KEY));
        assertFalse(legacy.is(DiagCode.E_DUP_KEY)); // the intentionally-distinct near-twin
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
}
