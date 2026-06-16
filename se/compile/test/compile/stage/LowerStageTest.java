package compile.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.MapSpecRegistry;
import compile.SpecRegistry;
import compile.cond.VarBinding;
import compile.cond.VarKind;
import compile.cond.VarResolver;
import compile.def.AbilityDef;
import compile.model.Affinity;
import compile.model.CompiledCondition;
import compile.model.CompiledEffect;
import compile.model.CompiledSelector;
import compile.model.SourceKind;
import compile.model.cond.Cond;
import schema.diag.Diagnostics;
import schema.diag.Source;
import schema.grammar.EffectLine;
import schema.spec.D;
import schema.spec.ParamSpec;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LowerStageTest {

    private static final Source SRC = Source.of("enchants.yml", 7, 1);

    private static ParamSpec damage() {
        return ParamSpec.of("DAMAGE")
                .param("amount", D.DOUBLE.min(0))
                .build();
    }

    private static ParamSpec heal() {
        return ParamSpec.of("HEAL")
                .param("amount", D.DOUBLE.min(0))
                .param("cooldown", D.TICKS.def(0))
                .build();
    }

    private static SpecRegistry registry() {
        return MapSpecRegistry.of(damage(), heal());
    }

    private static EffectLine line(String raw) {
        return EffectLine.parse(raw, SRC);
    }

    private static AbilityDef def(String conditionExpr, EffectLine... effects) {
        return new AbilityDef(
                SourceKind.ENCHANT,
                "test/ability",
                42,
                3,
                25.0,
                40,
                0,
                List.of("ATTACK"),
                List.of("world_nether"),
                conditionExpr,
                List.of(effects),
                null,
                null,
                null,
                null,
                0,
                SRC);
    }

    @Test
    void effectLineCompilesToCompiledEffectWithTypedArgs() {
        Diagnostics d = new Diagnostics();
        LoweredAbility lowered = new DefaultLowerStage(registry())
                .lower(def(null, line("DAMAGE:6")), d);

        assertFalse(d.hasErrors());
        assertEquals(1, lowered.effects().size());
        CompiledEffect e = lowered.effects().get(0);
        assertEquals("DAMAGE", e.head());
        assertEquals(6.0, e.args().dbl("amount"));
        assertSame(CompiledSelector.SELF, e.target());
        assertEquals(0, e.cumulativeWaitTicks());
    }

    @Test
    void appliesSpecDefaultsAndPreservesAbilityMetadata() {
        Diagnostics d = new Diagnostics();
        LoweredAbility lowered = new DefaultLowerStage(registry())
                .lower(def(null, line("HEAL:4")), d);

        assertFalse(d.hasErrors());
        assertEquals(0L, lowered.effects().get(0).args().lng("cooldown")); // default applied
        assertEquals(SourceKind.ENCHANT, lowered.sourceKind());
        assertEquals("test/ability", lowered.stableKey());
        assertEquals(42, lowered.defId());
        assertEquals(3, lowered.level());
        assertEquals(25.0, lowered.baseChance());
        assertEquals(40, lowered.cooldownTicks());
        assertEquals(List.of("ATTACK"), lowered.triggers());
        assertEquals(List.of("world_nether"), lowered.worldBlacklist());
        assertSame(SRC, lowered.source());
    }

    @Test
    void waitAccumulatesCumulativelyAndEmitsNoEffect() {
        Diagnostics d = new Diagnostics();
        LoweredAbility lowered = new DefaultLowerStage(registry()).lower(
                def(null,
                        line("DAMAGE:1"),   // A
                        line("WAIT:10"),
                        line("DAMAGE:2"),   // B
                        line("WAIT:5"),
                        line("DAMAGE:3")),  // C
                d);

        assertFalse(d.hasErrors());
        assertEquals(3, lowered.effects().size()); // no CompiledEffect for WAIT lines
        assertEquals(0, lowered.effects().get(0).cumulativeWaitTicks());  // A
        assertEquals(10, lowered.effects().get(1).cumulativeWaitTicks()); // B
        assertEquals(15, lowered.effects().get(2).cumulativeWaitTicks()); // C
    }

    @Test
    void unknownHeadIsSkippedAndDiagnosed() {
        Diagnostics d = new Diagnostics();
        LoweredAbility lowered = new DefaultLowerStage(registry())
                .lower(def(null, line("DAMAGE:1"), line("BOGUS:1")), d);

        assertTrue(d.hasErrors());
        assertEquals(1, lowered.effects().size()); // only DAMAGE survived
        assertEquals("DAMAGE", lowered.effects().get(0).head());
    }

    @Test
    void negativeWaitIsDiagnosedAndDoesNotCrash() {
        Diagnostics d = new Diagnostics();
        LoweredAbility lowered = new DefaultLowerStage(registry())
                .lower(def(null, line("WAIT:-1"), line("DAMAGE:1")), d);

        assertTrue(d.hasErrors());
        assertEquals("E_WAIT_ARG", d.all().get(0).code());
        assertEquals(1, lowered.effects().size());
        assertEquals(0, lowered.effects().get(0).cumulativeWaitTicks()); // bad WAIT ignored
    }

    @Test
    void nonIntegerWaitIsDiagnosed() {
        Diagnostics d = new Diagnostics();
        LoweredAbility lowered = new DefaultLowerStage(registry())
                .lower(def(null, line("WAIT:abc"), line("DAMAGE:1")), d);

        assertTrue(d.hasErrors());
        assertEquals("E_WAIT_ARG", d.all().get(0).code());
        assertEquals(0, lowered.effects().get(0).cumulativeWaitTicks());
    }

    @Test
    void wrongArgCountWaitIsDiagnosed() {
        Diagnostics d = new Diagnostics();
        LoweredAbility lowered = new DefaultLowerStage(registry())
                .lower(def(null, line("WAIT"), line("WAIT:10:20"), line("DAMAGE:1")), d);

        assertTrue(d.hasErrors());
        assertEquals("E_WAIT_ARG", d.all().get(0).code());
        assertEquals("E_WAIT_ARG", d.all().get(1).code());
        // Both malformed WAITs were ignored, so no delay accrued.
        assertEquals(0, lowered.effects().get(0).cumulativeWaitTicks());
    }

    @Test
    void validConditionLowersToATypedCompiledCondition() {
        Diagnostics d = new Diagnostics();
        VarResolver vars = (scope, name) -> {
            String key = scope == null ? name : scope + "." + name;
            return switch (key) {
                case "victim.health" -> Optional.of(new VarBinding(VarKind.NUM, 0));
                case "blocking" -> Optional.of(new VarBinding(VarKind.BOOL, 0));
                default -> Optional.empty();
            };
        };
        DefaultLowerStage stage = new DefaultLowerStage(registry(), head -> Affinity.CONTEXT_LOCAL,
                MapSpecRegistry.of(), head -> null, vars);
        LoweredAbility lowered = stage.lower(def("%victim.health% < 5 && !%blocking%", line("DAMAGE:1")), d);

        assertFalse(d.hasErrors());
        CompiledCondition condition = lowered.condition();
        assertNotNull(condition);
        assertEquals(SRC, condition.source());
        assertTrue(condition.root() instanceof Cond.And); // top-level && lowered to a typed node
    }

    @Test
    void blankOrNullConditionLowersToNull() {
        Diagnostics d = new Diagnostics();
        DefaultLowerStage stage = new DefaultLowerStage(registry());

        assertNull(stage.lower(def(null, line("DAMAGE:1")), d).condition());
        assertNull(stage.lower(def("   ", line("DAMAGE:1")), d).condition());
        assertFalse(d.hasErrors());
    }

    @Test
    void affinityFoldsToTheWidestEffectAffinity() {
        Diagnostics d = new Diagnostics();
        // DAMAGE -> AOE, HEAL -> CONTEXT_LOCAL; the fold MAX is AOE.
        DefaultLowerStage stage = new DefaultLowerStage(registry(),
                head -> "DAMAGE".equals(head) ? Affinity.AOE : Affinity.CONTEXT_LOCAL);

        LoweredAbility lowered = stage.lower(def(null, line("HEAL:1"), line("DAMAGE:1")), d);

        assertFalse(d.hasErrors());
        assertEquals(Affinity.AOE, lowered.effects().get(1).affinity());
        assertEquals(Affinity.AOE, lowered.affinity());
    }

    @Test
    void noEffectsFoldsToContextLocal() {
        Diagnostics d = new Diagnostics();
        LoweredAbility lowered = new DefaultLowerStage(registry()).lower(def(null), d);

        assertFalse(d.hasErrors());
        assertTrue(lowered.effects().isEmpty());
        assertEquals(Affinity.CONTEXT_LOCAL, lowered.affinity());
    }
}
