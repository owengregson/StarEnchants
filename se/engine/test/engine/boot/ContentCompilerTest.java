package engine.boot;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import compile.def.AbilityDef;
import compile.model.Snapshot;
import compile.model.SourceKind;
import engine.trigger.BuiltinTriggers;
import java.util.List;
import org.junit.jupiter.api.Test;
import schema.diag.Diagnostics;
import schema.diag.Source;
import schema.grammar.EffectLine;

/**
 * Smoke test for the production compiler wiring (no server): catches a wiring regression — a duplicate kind
 * head, a missing default selector — before any load. Building must succeed and a hand-built ability using a
 * real built-in effect + the canonical trigger vocabulary must compile to a resolvable snapshot entry.
 */
class ContentCompilerTest {

    @Test
    void productionCompilerCompilesABuiltinEffectAbility() {
        String trigger = BuiltinTriggers.registry().names().get(0); // a guaranteed-valid trigger
        AbilityDef def = new AbilityDef(
                SourceKind.ENCHANT,
                "enchants/test/1",
                0,
                1,
                100.0,
                0,
                0,
                List.of(trigger),
                List.of(),
                null,
                List.of(EffectLine.parse("IGNITE:60", Source.ofFile("test.yml"))),
                "enchants/test",
                "enchants/test",
                null,
                null,
                0,
                Source.ofFile("test.yml"),
                0);

        Diagnostics diags = new Diagnostics();
        Snapshot snapshot = ContentCompiler.production().compile(List.of(def), 1, diags);

        assertFalse(diags.hasErrors(), () -> diags.all().toString());
        assertNotNull(snapshot.byStableKey("enchants/test/1"));
    }
}
