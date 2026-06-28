package engine.boot;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import compile.def.AbilityDef;
import compile.model.Snapshot;
import engine.trigger.BuiltinTriggers;
import java.util.List;
import org.junit.jupiter.api.Test;
import schema.diag.Diagnostics;
import schema.diag.Source;
import testfx.Defs;

/** Smoke test catching a production-wiring regression (duplicate kind head, missing default selector) before any load. */
class ContentCompilerTest {

    @Test
    void productionCompilerCompilesABuiltinEffectAbility() {
        String trigger = BuiltinTriggers.registry().names().get(0); // a guaranteed-valid trigger
        AbilityDef def = Defs.ability()
                .stableKey("enchants/test/1").defId(0).triggers(trigger)
                .effectLines("IGNITE:60").suppressKey("enchants/test")
                .cooldownScope("enchants/test", null, null)
                .source(Source.ofFile("test.yml"))
                .build();

        Diagnostics diags = new Diagnostics();
        Snapshot snapshot = ContentCompiler.production().compile(List.of(def), 1, diags);

        assertFalse(diags.hasErrors(), () -> diags.all().toString());
        assertNotNull(snapshot.byStableKey("enchants/test/1"));
    }
}
