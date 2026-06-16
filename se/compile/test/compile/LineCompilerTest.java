package compile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import schema.diag.Diagnostics;
import schema.diag.Source;
import schema.spec.D;
import schema.spec.ParamSpec;
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
        assertEquals(0L, r.get().args().lng("cooldown")); // default applied
    }

    @Test
    void unknownHeadIsSkippedWithDiagnostic() {
        Diagnostics d = new Diagnostics();
        Optional<CompiledLine> r = compiler().compile("WHATEVER:1:2", SRC, d);
        assertTrue(r.isEmpty());
        assertTrue(d.hasErrors());
        assertEquals("E_UNKNOWN_KIND", d.all().get(0).code());
    }

    @Test
    void argumentErrorsStillReturnTheLineForTheCallerToReject() {
        Diagnostics d = new Diagnostics();
        Optional<CompiledLine> r = compiler().compile("SMITE:notanumber:4:6", SRC, d);
        assertTrue(r.isPresent());
        assertTrue(d.hasErrors());
        assertEquals("E_TYPE", d.all().get(0).code());
    }

    @Test
    void duplicateHeadsAreRejectedAtRegistryConstruction() {
        assertThrows(IllegalArgumentException.class, () -> MapSpecRegistry.of(smite(), smite()));
    }
}
