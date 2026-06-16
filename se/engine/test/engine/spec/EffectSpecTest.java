package engine.spec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.model.Affinity;
import schema.diag.Diagnostics;
import schema.diag.Source;
import schema.spec.Args;
import schema.spec.D;
import java.util.List;
import org.junit.jupiter.api.Test;

class EffectSpecTest {

    @Test
    void wrapsParamSpecAndDeclaresAffinityAndTargets() {
        EffectSpec spec = EffectSpec.of("SMITE")
                .param("chance", D.DOUBLE.min(0).max(100))
                .param("damage", D.DOUBLE.min(0))
                .target("who", T.AOE)
                .affinity(Affinity.AOE)
                .doc("Lightning + AoE damage near the target.")
                .example("SMITE:25:6")
                .build();

        assertEquals("SMITE", spec.head());
        assertEquals(Affinity.AOE, spec.affinity());
        assertEquals(List.of(new TargetSpec("who", T.AOE)), spec.targets());
        assertEquals("Lightning + AoE damage near the target.", spec.doc());
        assertEquals("SMITE:25:6", spec.example());

        // The wrapped ParamSpec still validates arguments into typed values.
        Diagnostics d = new Diagnostics();
        Args args = spec.paramSpec().parse(List.of("25", "6"), Source.UNKNOWN, d);
        assertFalse(d.hasErrors());
        assertEquals(25.0, args.dbl("chance"));
        assertEquals(6.0, args.dbl("damage"));
    }

    @Test
    void affinityDefaultsToContextLocalAndNoTargets() {
        EffectSpec spec = EffectSpec.of("PING").param("a", D.DOUBLE).build();
        assertEquals(Affinity.CONTEXT_LOCAL, spec.affinity());
        assertTrue(spec.targets().isEmpty());
    }
}
