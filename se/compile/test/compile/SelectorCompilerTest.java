package compile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.model.CompiledSelector;
import schema.diag.Diagnostics;
import schema.diag.Source;
import schema.spec.D;
import schema.spec.ParamSpec;
import org.junit.jupiter.api.Test;

class SelectorCompilerTest {

    private static final Source SRC = Source.of("enchants.yml", 1, 1);

    private static SelectorCompiler compiler() {
        SpecRegistry selectors = MapSpecRegistry.of(
                ParamSpec.of("AOE").param("r", D.DOUBLE.min(0).def(4)).build(),
                ParamSpec.of("NEAREST").param("r", D.DOUBLE.min(0).def(5)).build());
        return new SelectorCompiler(selectors);
    }

    @Test
    void inlineSelectorValidatesAndTypesArgs() {
        Diagnostics d = new Diagnostics();
        CompiledSelector sel = compiler().compileInline("@Aoe{r=3}", SRC, d);
        assertFalse(d.hasErrors());
        assertEquals("AOE", sel.head());
        assertEquals(3.0, sel.args().dbl("r"));
    }

    @Test
    void inlineSelectorAppliesArgDefaults() {
        Diagnostics d = new Diagnostics();
        CompiledSelector sel = compiler().compileInline("@Aoe", SRC, d);
        assertFalse(d.hasErrors());
        assertEquals("AOE", sel.head());
        assertEquals(4.0, sel.args().dbl("r"));
    }

    @Test
    void unknownSelectorIsDiagnosedAndFallsBackToSelf() {
        Diagnostics d = new Diagnostics();
        CompiledSelector sel = compiler().compileInline("@Bogus{r=1}", SRC, d);
        assertTrue(d.hasErrors());
        assertEquals("E_UNKNOWN_SELECTOR", d.all().get(0).code());
        assertSame(CompiledSelector.SELF, sel);
    }

    @Test
    void outOfRangeArgIsDiagnosed() {
        Diagnostics d = new Diagnostics();
        compiler().compileInline("@Aoe{r=-1}", SRC, d);
        assertTrue(d.hasErrors());
        assertEquals("E_RANGE", d.all().get(0).code());
    }

    @Test
    void unknownArgWarnsButStillCompilesWithDefaults() {
        Diagnostics d = new Diagnostics();
        CompiledSelector sel = compiler().compileInline("@Aoe{radius=3}", SRC, d);
        assertFalse(d.hasErrors()); // unknown arg is a warning, not an error
        assertEquals("W_SELECTOR_UNKNOWN_ARG", d.all().get(0).code());
        assertEquals(4.0, sel.args().dbl("r"));
    }

    @Test
    void malformedSelectorSyntaxFallsBackToSelf() {
        Diagnostics d = new Diagnostics();
        CompiledSelector sel = compiler().compileInline("@Aoe{r=1", SRC, d);
        assertTrue(d.hasErrors());
        assertSame(CompiledSelector.SELF, sel);
    }

    @Test
    void defaultForResolvesDeclaredTargetWithDefaults() {
        Diagnostics d = new Diagnostics();
        CompiledSelector sel = compiler().defaultFor("AOE", SRC, d);
        assertFalse(d.hasErrors());
        assertEquals("AOE", sel.head());
        assertEquals(4.0, sel.args().dbl("r"));
    }

    @Test
    void defaultForNullOrSelfIsTheSelfConstant() {
        Diagnostics d = new Diagnostics();
        assertSame(CompiledSelector.SELF, compiler().defaultFor(null, SRC, d));
        assertSame(CompiledSelector.SELF, compiler().defaultFor("self", SRC, d));
        assertSame(CompiledSelector.SELF, compiler().defaultFor("  ", SRC, d));
        assertFalse(d.hasErrors());
    }
}
