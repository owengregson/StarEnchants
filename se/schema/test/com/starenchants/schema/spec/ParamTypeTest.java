package com.starenchants.schema.spec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.starenchants.schema.diag.Diagnostics;
import com.starenchants.schema.diag.Source;
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
        assertEquals("E_TYPE", d.all().get(0).code());
    }

    @Test
    void doubleEnforcesRange() {
        Diagnostics over = new Diagnostics();
        assertTrue(D.DOUBLE.min(0).max(100).parse("150", SRC, over).isEmpty());
        assertEquals("E_RANGE", over.all().get(0).code());

        Diagnostics under = new Diagnostics();
        assertTrue(D.DOUBLE.min(0).max(100).parse("-1", SRC, under).isEmpty());
        assertEquals("E_RANGE", under.all().get(0).code());

        Diagnostics ok = new Diagnostics();
        assertEquals(0.0, dbl(D.DOUBLE.min(0).max(100).parse("0", SRC, ok)));
        assertFalse(ok.hasErrors());
    }

    @Test
    void intRejectsDecimals() {
        Diagnostics d = new Diagnostics();
        assertTrue(D.INT.parse("3.5", SRC, d).isEmpty());
        assertEquals("E_TYPE", d.all().get(0).code());

        Diagnostics ok = new Diagnostics();
        assertEquals(3L, D.INT.parse("3", SRC, ok).orElseThrow());
        assertFalse(ok.hasErrors());
    }

    @Test
    void ticksAreFlooredAtZero() {
        Diagnostics d = new Diagnostics();
        assertTrue(D.TICKS.parse("-1", SRC, d).isEmpty());
        assertEquals("E_RANGE", d.all().get(0).code());
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
        assertEquals("E_TYPE", d.all().get(0).code());
    }

    @Test
    void enumNormalizesToCanonicalSpelling() {
        ParamType shape = D.enumOf("CIRCLE", "SQUARE");
        assertEquals("CIRCLE", shape.parse("circle", SRC, new Diagnostics()).orElseThrow());

        Diagnostics d = new Diagnostics();
        assertTrue(shape.parse("triangle", SRC, d).isEmpty());
        assertEquals("E_ENUM", d.all().get(0).code());
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
}
