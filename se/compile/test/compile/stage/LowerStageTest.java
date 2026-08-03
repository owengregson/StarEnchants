package compile.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
import schema.grammar.expr.FlowKind;
import compile.model.CompiledEffect;
import compile.model.CompiledSelector;
import compile.model.SourceKind;
import compile.model.cond.Cond;
import compile.model.FactMask;
import compile.model.FactMasks;
import compile.model.cond.NumExpr;
import compile.model.cond.NumExprMap;
import schema.diag.DiagCode;
import schema.diag.Diagnostics;
import schema.diag.Source;
import schema.grammar.EffectLine;
import schema.spec.D;
import schema.spec.ParamSpec;
import testfx.Defs;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LowerStageTest {

    private static final Source SRC = Source.of("enchants.yml", 7, 1);

    private static final VarResolver VARS = (scope, name) ->
            "damage".equals(scope == null ? name : scope + "." + name)
                    ? Optional.of(new VarBinding(VarKind.NUM, 0)) : Optional.empty();

    private static ParamSpec damage() {
        return ParamSpec.of("DAMAGE")
                .param("amount", D.DOUBLE.min(0))
                .build();
    }

    /** A spec whose only param is an EXPR_MAP — the shape MESSAGE's {@code tokens} introduced. */
    private static ParamSpec noted() {
        return ParamSpec.of("NOTE")
                .param("marks", D.exprMap())
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
        return Defs.ability()
                .stableKey("test/ability").defId(42).level(3).chance(25.0).cooldown(40)
                .triggers("ATTACK").worldBlacklist("world_nether")
                .condition(conditionExpr).effects(effects).source(SRC)
                .build();
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
    void anExpressionArgLowersUnderTheParamSpecRange() {
        // The end-to-end range rule: a param declaring a bound wraps its expression so evaluation is confined,
        // while the arithmetic the author wrote survives underneath. `amount` is DOUBLE.min(0).
        Diagnostics d = new Diagnostics();
        LoweredAbility lowered = new DefaultLowerStage(registry(), head -> Affinity.CONTEXT_LOCAL,
                MapSpecRegistry.of(), head -> null, VARS)
                .lower(def(null, line("DAMAGE:%damage% - 100")), d);

        assertFalse(d.hasErrors());
        NumExpr.Fn fn = assertInstanceOf(NumExpr.Fn.class, lowered.effects().get(0).args().opt("amount").orElseThrow());
        assertEquals(NumExpr.FnKind.CLAMP, fn.kind());
        assertInstanceOf(NumExpr.Bin.class, fn.args().get(0));
        assertEquals(new NumExpr.Lit(0.0), fn.args().get(1));
    }

    @Test
    void everyBindingOfAnExprMapArgLowersToItsOwnExpression() {
        // The whole point of a dedicated map type: the lowering walk must descend INTO it. A map left opaque
        // reaches the runtime as unlowered Expr trees, which evaluate to nothing at all.
        Diagnostics d = new Diagnostics();
        LoweredAbility lowered = new DefaultLowerStage(MapSpecRegistry.of(noted()),
                head -> Affinity.CONTEXT_LOCAL, MapSpecRegistry.of(), head -> null, VARS)
                .lower(def(null, EffectLine.verbose("NOTE", 1,
                        Map.of("marks", "hit=%damage%; twice=%damage% * 2"), null, SRC)), d);

        assertFalse(d.hasErrors());
        NumExprMap marks = assertInstanceOf(NumExprMap.class,
                lowered.effects().get(0).args().opt("marks").orElseThrow());
        assertEquals(Set.of("hit", "twice"), marks.entries().keySet());
        assertEquals(new NumExpr.Var(0), marks.entries().get("hit"));
        assertInstanceOf(NumExpr.Bin.class, marks.entries().get("twice"));
    }

    @Test
    void anExprMapArgsSlotsReachTheAbilitysFactMask() {
        // A binding reads the fact buffer exactly as a scalar expression arg does, so its slots have to be in
        // the mask — an unmasked slot is never populated and the binding silently renders 0.
        Diagnostics d = new Diagnostics();
        LoweredAbility lowered = new DefaultLowerStage(MapSpecRegistry.of(noted()),
                head -> Affinity.CONTEXT_LOCAL, MapSpecRegistry.of(), head -> null, VARS)
                .lower(def(null, EffectLine.verbose("NOTE", 1, Map.of("marks", "hit=%damage%"), null, SRC)), d);

        assertFalse(d.hasErrors());
        FactMask mask = FactMasks.of(lowered.condition(), null,
                lowered.effects().toArray(new CompiledEffect[0]));
        assertTrue(mask.readsNum(0), "the %damage% slot the binding reads must be marked");
    }

    @Test
    void anAbsentExprMapArgLowersToNoBindings() {
        // The back-compat path: content that never mentions the param must reach the runtime with an empty
        // map, not a null the reader has to defend against.
        Diagnostics d = new Diagnostics();
        LoweredAbility lowered = new DefaultLowerStage(MapSpecRegistry.of(noted()),
                head -> Affinity.CONTEXT_LOCAL, MapSpecRegistry.of(), head -> null, VARS)
                .lower(def(null, EffectLine.verbose("NOTE", 1, Map.of(), null, SRC)), d);

        assertFalse(d.hasErrors());
        assertTrue(assertInstanceOf(NumExprMap.class,
                lowered.effects().get(0).args().opt("marks").orElseThrow()).isEmpty());
    }

    @Test
    void aConstantChanceLowersToThePrimitiveWithNoExpression() {
        // The fast path must stay byte-identical: a numeric chance: never grows a NumExpr to walk per hit.
        Diagnostics d = new Diagnostics();
        LoweredAbility lowered = new DefaultLowerStage(registry()).lower(def(null, line("DAMAGE:6")), d);

        assertFalse(d.hasErrors());
        assertEquals(25.0, lowered.baseChance(), 1e-9);
        assertNull(lowered.chanceExpr(), "a constant chance carries no expression");
    }

    @Test
    void anExpressionChanceLowersToANumExprBesideTheConstant() {
        Diagnostics d = new Diagnostics();
        AbilityDef def = Defs.ability()
                .stableKey("test/ability").defId(1).triggers("ATTACK")
                .chanceExpr("min(50, %damage% * 10)")
                .effects(line("DAMAGE:6")).source(SRC).build();
        LoweredAbility lowered = new DefaultLowerStage(registry(), head -> Affinity.CONTEXT_LOCAL,
                MapSpecRegistry.of(), head -> null, VARS).lower(def, d);

        assertFalse(d.hasErrors(), () -> d.all().toString());
        NumExpr.Fn fn = assertInstanceOf(NumExpr.Fn.class, lowered.chanceExpr());
        assertEquals(NumExpr.FnKind.MIN, fn.kind());
    }

    @Test
    void aMalformedChanceExpressionIsADiagnosticNotAThrow() {
        Diagnostics d = new Diagnostics();
        AbilityDef def = Defs.ability()
                .stableKey("test/ability").defId(1).triggers("ATTACK")
                .chanceExpr("min(1)")   // wrong arity
                .effects(line("DAMAGE:6")).source(SRC).build();
        LoweredAbility lowered = new DefaultLowerStage(registry()).lower(def, d);

        assertTrue(d.hasErrors());
        assertNull(lowered.chanceExpr(), "a rejected expression falls back to the constant chance");
    }

    @Test
    void aConstantArgOutOfTheParamSpecRangeStaysAnError() {
        Diagnostics d = new Diagnostics();
        new DefaultLowerStage(registry()).lower(def(null, line("DAMAGE:-5")), d);
        assertTrue(d.all().stream().anyMatch(x -> x.is(DiagCode.E_RANGE)));
    }

    @Test
    void appliesSpecDefaultsAndPreservesAbilityMetadata() {
        Diagnostics d = new Diagnostics();
        LoweredAbility lowered = new DefaultLowerStage(registry())
                .lower(def(null, line("HEAL:4")), d);

        assertFalse(d.hasErrors());
        assertEquals(0L, lowered.effects().get(0).args().lng("cooldown"));
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
        assertEquals(1, lowered.effects().size());
        assertEquals("DAMAGE", lowered.effects().get(0).head());
    }

    @Test
    void negativeWaitIsDiagnosedAndDoesNotCrash() {
        Diagnostics d = new Diagnostics();
        LoweredAbility lowered = new DefaultLowerStage(registry())
                .lower(def(null, line("WAIT:-1"), line("DAMAGE:1")), d);

        assertTrue(d.hasErrors());
        assertTrue(d.all().get(0).is(DiagCode.E_WAIT_ARG));
        assertEquals(1, lowered.effects().size());
        assertEquals(0, lowered.effects().get(0).cumulativeWaitTicks()); // bad WAIT ignored
    }

    @Test
    void nonIntegerWaitIsDiagnosed() {
        Diagnostics d = new Diagnostics();
        LoweredAbility lowered = new DefaultLowerStage(registry())
                .lower(def(null, line("WAIT:abc"), line("DAMAGE:1")), d);

        assertTrue(d.hasErrors());
        assertTrue(d.all().get(0).is(DiagCode.E_WAIT_ARG));
        assertEquals(0, lowered.effects().get(0).cumulativeWaitTicks());
    }

    @Test
    void wrongArgCountWaitIsDiagnosed() {
        Diagnostics d = new Diagnostics();
        LoweredAbility lowered = new DefaultLowerStage(registry())
                .lower(def(null, line("WAIT"), line("WAIT:10:20"), line("DAMAGE:1")), d);

        assertTrue(d.hasErrors());
        assertTrue(d.all().get(0).is(DiagCode.E_WAIT_ARG));
        assertTrue(d.all().get(1).is(DiagCode.E_WAIT_ARG));
        // both malformed WAITs ignored → no delay accrued
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
        assertTrue(condition.root() instanceof Cond.And);
        // a bare expression is a gate: pass → CONTINUE, fail → STOP, no chance delta
        assertEquals(FlowKind.CONTINUE, condition.whenTrue());
        assertEquals(FlowKind.STOP, condition.whenFalse());
        assertEquals(0.0, condition.chanceDelta());
    }

    @Test
    void flowAndChanceClausesLowerToTheAuthoredOutcome() {
        Diagnostics d = new Diagnostics();
        VarResolver vars = (scope, name) ->
                "blocking".equals(name) ? Optional.of(new VarBinding(VarKind.BOOL, 0)) : Optional.empty();
        DefaultLowerStage stage = new DefaultLowerStage(registry(), head -> Affinity.CONTEXT_LOCAL,
                MapSpecRegistry.of(), head -> null, vars);

        // %force% clause: whenTrue=FORCE, whenFalse=CONTINUE (a failing clause never stops)
        CompiledCondition force = stage.lower(def("%blocking% : %force%", line("DAMAGE:1")), d).condition();
        assertNotNull(force);
        assertEquals(FlowKind.FORCE, force.whenTrue());
        assertEquals(FlowKind.CONTINUE, force.whenFalse());
        assertTrue(force.root() instanceof Cond.BoolVar);

        // ±N %chance% clause: CONTINUE with the signed delta carried
        CompiledCondition chance = stage.lower(def("%blocking% : +40 %chance%", line("DAMAGE:1")), d).condition();
        assertNotNull(chance);
        assertEquals(FlowKind.CONTINUE, chance.whenTrue());
        assertEquals(40.0, chance.chanceDelta());
        assertFalse(d.hasErrors());
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
        // DAMAGE→AOE, HEAL→CONTEXT_LOCAL; fold takes the MAX → AOE
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
