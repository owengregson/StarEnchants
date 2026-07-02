package compile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import schema.diag.DiagCode;
import schema.diag.Diagnostics;
import schema.diag.Source;
import schema.grammar.EffectLine;
import schema.spec.D;
import schema.spec.ParamSpec;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LineCompilerTest {

    private static final Source SRC = Source.of("effects.yml", 12, 1);

    private static ParamSpec smite() {
        return ParamSpec.of("SMITE")
                .param("chance", D.DOUBLE.min(0).max(100))
                .param("radius", D.DOUBLE.min(0))
                .param("damage", D.DOUBLE.min(0))
                .param("cooldown", D.TICKS.def(0))
                .example("SMITE:25:4:6:40")
                .build();
    }

    private static LineCompiler compiler() {
        return new LineCompiler(MapSpecRegistry.of(smite()));
    }

    @Test
    void compilesValidLineToTypedArgs() {
        Diagnostics d = new Diagnostics();
        Optional<CompiledLine> r = compiler().compile("SMITE:25:4:6:40", SRC, d);
        assertFalse(d.hasErrors());
        assertTrue(r.isPresent());
        assertEquals("SMITE", r.get().head());
        assertEquals(6.0, r.get().args().dbl("damage"));
        assertEquals(40L, r.get().args().lng("cooldown"));
    }

    @Test
    void headLookupIsCaseInsensitiveAndAppliesDefaults() {
        Diagnostics d = new Diagnostics();
        Optional<CompiledLine> r = compiler().compile("smite:25:4:6", SRC, d);
        assertFalse(d.hasErrors());
        assertTrue(r.isPresent());
        assertEquals(0L, r.get().args().lng("cooldown"));
    }

    @Test
    void unknownHeadIsSkippedWithDiagnostic() {
        Diagnostics d = new Diagnostics();
        Optional<CompiledLine> r = compiler().compile("WHATEVER:1:2", SRC, d);
        assertTrue(r.isEmpty());
        assertTrue(d.hasErrors());
        assertTrue(d.all().get(0).is(DiagCode.E_UNKNOWN_KIND));
    }

    @Test
    void argumentErrorsStillReturnTheLineForTheCallerToReject() {
        Diagnostics d = new Diagnostics();
        Optional<CompiledLine> r = compiler().compile("SMITE:notanumber:4:6", SRC, d);
        assertTrue(r.isPresent());
        assertTrue(d.hasErrors());
        assertTrue(d.all().get(0).is(DiagCode.E_TYPE));
    }

    @Test
    void duplicateHeadsAreRejectedAtRegistryConstruction() {
        assertThrows(IllegalArgumentException.class, () -> MapSpecRegistry.of(smite(), smite()));
    }

    // 'label'/'boost' are optional with NO default — the shape that exposed toPositional's fabricated "".
    private static ParamSpec mark() {
        return ParamSpec.of("MARK")
                .param("power", D.DOUBLE.min(0))
                .param("label", D.STRING.optional())
                .param("boost", D.DOUBLE.min(0).optional())
                .build();
    }

    private static LineCompiler markCompiler() {
        return new LineCompiler(MapSpecRegistry.of(mark()));
    }

    /** Absent optional-no-default in a verbose line is genuinely absent: no diagnostic, not present in args. */
    @Test
    void verboseLineOmittingOptionalNoDefaultLeavesItAbsent() {
        Map<String, String> named = new LinkedHashMap<>();
        named.put("power", "5");
        Diagnostics d = new Diagnostics();
        Optional<CompiledLine> r = markCompiler().compile(EffectLine.verbose("MARK", 1, named, null, SRC), d);
        assertFalse(d.hasErrors());
        assertEquals(0, d.all().size());               // no spurious E_TYPE on the non-STRING optional
        assertTrue(r.isPresent());
        assertEquals(5.0, r.get().args().dbl("power"));
        assertFalse(r.get().args().has("boost"));      // non-STRING would have failed parse("")
        assertFalse(r.get().args().has("label"));      // STRING would have been admitted as a present ""
    }

    @Test
    void verboseLineWithOptionalNoDefaultRoundTrips() {
        Map<String, String> named = new LinkedHashMap<>();
        named.put("power", "5");
        named.put("label", "hello");
        named.put("boost", "2.5");
        Diagnostics d = new Diagnostics();
        Optional<CompiledLine> r = markCompiler().compile(EffectLine.verbose("MARK", 1, named, null, SRC), d);
        assertFalse(d.hasErrors());
        assertTrue(r.isPresent());
        assertEquals("hello", r.get().args().str("label"));
        assertEquals(2.5, r.get().args().dbl("boost"));
    }
}
