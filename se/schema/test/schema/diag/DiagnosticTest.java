package schema.diag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DiagnosticTest {

    @Test
    void sourceRendersFileLineCol() {
        assertEquals("e.yml:4:12", Source.of("e.yml", 4, 12).toString());
        assertTrue(Source.of("e.yml", 4, 12).known());
    }

    @Test
    void unknownSourceRendersJustTheLabel() {
        assertEquals("<unknown>", Source.UNKNOWN.toString());
        assertFalse(Source.UNKNOWN.known());
    }

    @Test
    void atColumnShiftsColumnOnSameLine() {
        Source s = Source.of("e.yml", 4, 1).atColumn(9);
        assertEquals(4, s.line());
        assertEquals(9, s.col());
    }

    @Test
    void renderIncludesSeverityCodeMessageAndHint() {
        Diagnostic d = Diagnostic.error("E_RANGE",
                "value 150 is above the maximum 100", Source.of("e.yml", 4, 12), "lower it");
        assertEquals("e.yml:4:12: error[E_RANGE]: value 150 is above the maximum 100 (hint: lower it)",
                d.render());
    }

    @Test
    void renderOmitsHintWhenAbsent() {
        Diagnostic d = Diagnostic.warning("W_EXTRA_ARGS", "ignored 1 extra argument",
                Source.of("e.yml", 4, 1));
        assertEquals("e.yml:4:1: warning[W_EXTRA_ARGS]: ignored 1 extra argument", d.render());
    }

    @Test
    void severityErrorIsBlockingOthersAreNot() {
        assertTrue(Severity.ERROR.blocking());
        assertFalse(Severity.WARNING.blocking());
        assertFalse(Severity.INFO.blocking());
    }

    @Test
    void collectorTracksErrorsAndCounts() {
        Diagnostics diags = new Diagnostics();
        diags.warning("W_X", "warn", Source.UNKNOWN);
        assertFalse(diags.hasErrors());
        diags.error("E_Y", "boom", Source.UNKNOWN);
        assertTrue(diags.hasErrors());
        assertEquals(2, diags.size());
        assertEquals(1, diags.count(Severity.ERROR));
        assertEquals(1, diags.count(Severity.WARNING));
    }

    @Test
    void mergeAppendsAllDiagnostics() {
        Diagnostics a = new Diagnostics().info("I_A", "a", Source.UNKNOWN);
        Diagnostics b = new Diagnostics().error("E_B", "b", Source.UNKNOWN);
        a.merge(b);
        assertEquals(2, a.size());
        assertTrue(a.hasErrors());
    }
}
