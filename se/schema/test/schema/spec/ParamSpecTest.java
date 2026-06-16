package schema.spec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import schema.diag.Diagnostic;
import schema.diag.Diagnostics;
import schema.diag.Severity;
import schema.diag.Source;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ParamSpecTest {

    private static final Source SRC = Source.of("effects.yml", 7, 1);

    /** Mirrors the worked example in docs/architecture.md §7. */
    private static ParamSpec smite() {
        return ParamSpec.of("SMITE")
                .param("chance", D.DOUBLE.min(0).max(100))
                .param("radius", D.DOUBLE.min(0))
                .param("damage", D.DOUBLE.min(0))
                .param("cooldown", D.TICKS.def(0))
                .rule((args, src, diags) -> {
                    if (args.dbl("radius") > 16) {
                        diags.error("E_RULE", "radius must not exceed 16", src);
                    }
                })
                .doc("Lightning + AoE damage near the target.")
                .example("SMITE:25:4:6:40")
                .build();
    }

    private static String firstCode(Diagnostics d) {
        return d.all().get(0).code();
    }

    @Test
    void validLineParsesToTypedArgs() {
        Diagnostics d = new Diagnostics();
        Args a = smite().parse(List.of("25", "4", "6", "40"), SRC, d);
        assertFalse(d.hasErrors());
        assertEquals(25.0, a.dbl("chance"));
        assertEquals(4.0, a.dbl("radius"));
        assertEquals(6.0, a.dbl("damage"));
        assertEquals(40L, a.lng("cooldown"));
    }

    @Test
    void optionalDefaultAppliedWhenAbsent() {
        Diagnostics d = new Diagnostics();
        Args a = smite().parse(List.of("25", "4", "6"), SRC, d);
        assertFalse(d.hasErrors());
        assertEquals(0L, a.lng("cooldown"));
    }

    @Test
    void missingRequiredArgIsAnError() {
        Diagnostics d = new Diagnostics();
        smite().parse(List.of("25", "4"), SRC, d); // damage missing
        assertTrue(d.hasErrors());
        Diagnostic err = d.all().get(0);
        assertEquals("E_MISSING_ARG", err.code());
        assertTrue(err.message().contains("damage"), err.message());
    }

    @Test
    void typeErrorReportedPerArgument() {
        Diagnostics d = new Diagnostics();
        smite().parse(List.of("notanumber", "4", "6"), SRC, d);
        assertTrue(d.hasErrors());
        assertEquals("E_TYPE", firstCode(d));
    }

    @Test
    void extraArgumentsWarnButDoNotBlock() {
        Diagnostics d = new Diagnostics();
        smite().parse(List.of("25", "4", "6", "40", "99"), SRC, d);
        assertFalse(d.hasErrors());
        assertEquals(1, d.count(Severity.WARNING));
        assertEquals("W_EXTRA_ARGS", firstCode(d));
    }

    @Test
    void crossRuleRunsOnlyWhenArgsAreClean() {
        // radius 20 parses fine (no max on the type) but violates the cross-rule.
        Diagnostics d = new Diagnostics();
        smite().parse(List.of("25", "20", "6", "0"), SRC, d);
        assertTrue(d.hasErrors());
        assertEquals("E_RULE", firstCode(d));

        // A type error short-circuits the cross-rule (no cascading noise).
        Diagnostics d2 = new Diagnostics();
        smite().parse(List.of("25", "notanumber", "6", "0"), SRC, d2);
        assertEquals("E_TYPE", firstCode(d2));
        assertEquals(1L, d2.count(Severity.ERROR)); // exactly one error: the type error, not the rule
    }

    @Test
    void usageRendersTheFullSignature() {
        assertEquals(
                "SMITE:<chance:double[0..100]>:<radius:double[0..]>:<damage:double[0..]>[:cooldown:ticks[0..]=0]",
                smite().usage());
    }

    @Test
    void toPositionalReordersNamedArgsForMigration() {
        Map<String, String> named = Map.of("damage", "6", "chance", "25", "radius", "4");
        assertEquals(List.of("25", "4", "6", "0"), smite().toPositional(named));
    }

    @Test
    void completionsDelegateToTheArgType() {
        ParamSpec spec = ParamSpec.of("AURA")
                .param("shape", D.enumOf("CIRCLE", "SQUARE"))
                .param("power", D.DOUBLE.min(0))
                .build();
        assertEquals(List.of("SQUARE"), spec.completions(0, "s"));
        assertTrue(spec.completions(1, "").isEmpty());
        assertTrue(spec.completions(5, "").isEmpty());
    }
}
