package compile.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.Compiler;
import compile.MapSpecRegistry;
import compile.SpecRegistry;
import compile.def.AbilityDef;
import compile.model.Ability;
import compile.model.Affinity;
import compile.model.CompiledEffect;
import compile.model.Snapshot;
import compile.resolve.FakeResolvers;
import compile.resolve.PlatformResolvers;
import schema.diag.DiagCode;
import schema.diag.Diagnostics;
import schema.diag.Source;
import schema.spec.D;
import schema.spec.ParamSpec;
import testfx.Defs;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResolveStageTest {

    private static final Source SRC = Source.of("enchants.yml", 1, 1);

    private static ParamSpec potion() {
        return ParamSpec.of("POTION")
                .param("effect", D.potionEffect())
                .param("amplifier", D.INT.min(0))
                .param("duration", D.TICKS)
                .build();
    }

    private static SpecRegistry potionRegistry() {
        return MapSpecRegistry.of(potion());
    }

    private static AbilityDef def(String... effectLines) {
        // The 18-arg AbilityDef ctor lives once in testfx.Defs now; this test states only what it varies.
        return Defs.ability().stableKey("ench/x").source(SRC).effectLines(effectLines).build();
    }

    private static LoweredAbility lower(SpecRegistry reg, AbilityDef def, Diagnostics d) {
        return new DefaultLowerStage(reg).lower(def, d);
    }

    @Test
    void resolvesHandleTokenToInternedIdAndLeavesOtherArgs() {
        SpecRegistry reg = potionRegistry();
        Diagnostics d = new Diagnostics();
        LoweredAbility lowered = lower(reg, def("POTION:STRENGTH:1:100"), d);
        assertFalse(d.hasErrors());

        PlatformResolvers resolvers = FakeResolvers.builder().potionEffect("STRENGTH", 7).build();
        LoweredAbility resolved = new DefaultResolveStage(reg, resolvers).resolve(lowered, d);

        assertFalse(d.hasErrors());
        assertEquals(1, resolved.effects().size());
        CompiledEffect e = resolved.effects().get(0);
        assertEquals(7, e.args().integer("effect")); // token → interned id
        assertEquals(1L, e.args().lng("amplifier")); // non-handle args untouched
        assertEquals(100L, e.args().lng("duration"));
    }

    @Test
    void unknownHandleIsReportedAndTheEffectDropped() {
        SpecRegistry reg = potionRegistry();
        Diagnostics d = new Diagnostics();
        LoweredAbility lowered = lower(reg, def("POTION:BOGUS:1:100"), d);
        assertFalse(d.hasErrors());

        LoweredAbility resolved =
                new DefaultResolveStage(reg, PlatformResolvers.none()).resolve(lowered, d);

        assertTrue(d.hasErrors());
        assertTrue(d.all().get(0).is(DiagCode.E_UNKNOWN_HANDLE));
        assertTrue(resolved.effects().isEmpty(), "the one unresolved effect is warn-and-skipped");
    }

    @Test
    void handleFreeEffectsPassThroughUnchanged() {
        ParamSpec heal = ParamSpec.of("HEAL").param("amount", D.DOUBLE.min(0)).build();
        SpecRegistry reg = MapSpecRegistry.of(heal);
        Diagnostics d = new Diagnostics();
        LoweredAbility lowered = lower(reg, def("HEAL:5"), d);

        LoweredAbility resolved =
                new DefaultResolveStage(reg, PlatformResolvers.none()).resolve(lowered, d);

        assertFalse(d.hasErrors());
        assertEquals(1, resolved.effects().size());
        assertEquals(5.0, resolved.effects().get(0).args().dbl("amount"));
    }

    @Test
    void compilerResolvesHandlesEndToEnd() {
        SpecRegistry reg = potionRegistry();
        PlatformResolvers resolvers = FakeResolvers.builder().potionEffect("STRENGTH", 7).build();
        Compiler compiler = Compiler.of(reg, head -> Affinity.CONTEXT_LOCAL, resolvers);

        Diagnostics d = new Diagnostics();
        Snapshot snap = compiler.compile(List.of(def("POTION:STRENGTH:1:100")), 1, d);

        assertFalse(d.hasErrors());
        Ability a = snap.byStableKey("ench/x");
        assertEquals(7, a.effects()[0].args().integer("effect"));
    }
}
