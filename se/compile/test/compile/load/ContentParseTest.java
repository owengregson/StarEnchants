package compile.load;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import schema.diag.DiagCode;
import schema.diag.Diagnostics;
import schema.diag.Severity;
import schema.diag.Source;

/** The one ADR-0042 number/bool parse family: content numbers block, config/item/menu numbers warn. */
class ContentParseTest {

    private static final Source AT = Source.UNKNOWN;

    @Test
    void intOrNullYieldsFallbackWithNoDiagnostics() {
        Diagnostics diags = new Diagnostics();
        assertEquals(5, ContentParse.intOr(null, 5, "x", Severity.ERROR, DiagCode.E_LOAD_INT, AT, diags));
        assertTrue(diags.isEmpty());
    }

    @Test
    void intOrParsesAValue() {
        Diagnostics diags = new Diagnostics();
        assertEquals(7, ContentParse.intOr("7", 0, "x", Severity.ERROR, DiagCode.E_LOAD_INT, AT, diags));
        assertTrue(diags.isEmpty());
    }

    @Test
    void intOrStrictNonNumberIsABlockingLoadInt() {
        Diagnostics diags = new Diagnostics();
        assertEquals(3, ContentParse.intOr("x", 3, "k", Severity.ERROR, DiagCode.E_LOAD_INT, AT, diags));
        assertEquals(1, diags.size());
        assertTrue(diags.all().get(0).is(DiagCode.E_LOAD_INT));
        assertTrue(diags.all().get(0).blocking());
    }

    @Test
    void intOrLenientNonNumberIsANonBlockingConfigNum() {
        Diagnostics diags = new Diagnostics();
        assertEquals(3, ContentParse.intOr("x", 3, "k", Severity.WARNING, DiagCode.W_CONFIG_NUM, AT, diags));
        assertEquals(1, diags.size());
        assertTrue(diags.all().get(0).is(DiagCode.W_CONFIG_NUM));
        assertFalse(diags.all().get(0).blocking());
    }

    @Test
    void intOrBlankIsPresentButUnparseable() {
        Diagnostics diags = new Diagnostics();
        assertEquals(3, ContentParse.intOr("  ", 3, "k", Severity.ERROR, DiagCode.E_LOAD_INT, AT, diags));
        assertEquals(1, diags.size());
        assertTrue(diags.all().get(0).is(DiagCode.E_LOAD_INT));
    }

    @Test
    void doubleOrParsesAndDiagnosesLikeInt() {
        Diagnostics ok = new Diagnostics();
        assertEquals(1.5, ContentParse.doubleOr("1.5", 0.0, "x", Severity.ERROR, DiagCode.E_LOAD_DOUBLE, AT, ok));
        assertTrue(ok.isEmpty());

        Diagnostics strict = new Diagnostics();
        assertEquals(2.0, ContentParse.doubleOr("nope", 2.0, "x", Severity.ERROR, DiagCode.E_LOAD_DOUBLE, AT, strict));
        assertTrue(strict.all().get(0).is(DiagCode.E_LOAD_DOUBLE) && strict.all().get(0).blocking());

        Diagnostics lenient = new Diagnostics();
        assertEquals(2.0, ContentParse.doubleOr("nope", 2.0, "x", Severity.WARNING, DiagCode.W_ITEM_NUM, AT, lenient));
        assertTrue(lenient.all().get(0).is(DiagCode.W_ITEM_NUM) && !lenient.all().get(0).blocking());
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "TRUE", "yes", "On", "1"})
    void boolOrTruthyVocabulary(String raw) {
        Diagnostics diags = new Diagnostics();
        assertTrue(ContentParse.boolOr(raw, false, "b", DiagCode.W_CONFIG_BOOL, AT, diags));
        assertTrue(diags.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"false", "FALSE", "no", "Off", "0"})
    void boolOrFalsyVocabulary(String raw) {
        Diagnostics diags = new Diagnostics();
        assertFalse(ContentParse.boolOr(raw, true, "b", DiagCode.W_CONFIG_BOOL, AT, diags));
        assertTrue(diags.isEmpty());
    }

    @Test
    void boolOrNullAndBlankFallBackSilently() {
        Diagnostics diags = new Diagnostics();
        assertTrue(ContentParse.boolOr(null, true, "b", DiagCode.W_CONFIG_BOOL, AT, diags));
        assertFalse(ContentParse.boolOr("", false, "b", DiagCode.W_CONFIG_BOOL, AT, diags));
        assertTrue(diags.isEmpty());
    }

    @Test
    void boolOrOutsideVocabularyWarnsAndFallsBack() {
        Diagnostics diags = new Diagnostics();
        assertTrue(ContentParse.boolOr("maybe", true, "b", DiagCode.W_CONFIG_BOOL, AT, diags));
        assertEquals(1, diags.size());
        assertTrue(diags.all().get(0).is(DiagCode.W_CONFIG_BOOL));
        assertFalse(diags.all().get(0).blocking());
    }
}
