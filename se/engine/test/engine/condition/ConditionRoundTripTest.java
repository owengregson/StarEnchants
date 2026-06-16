package engine.condition;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.Compiler;
import compile.def.AbilityDef;
import compile.model.Ability;
import compile.model.Snapshot;
import compile.model.SourceKind;
import engine.effect.EffectRegistry;
import engine.effect.kind.BuiltinEffects;
import engine.selector.SelectorRegistry;
import engine.selector.kind.BuiltinSelectors;
import schema.diag.Diagnostics;
import schema.diag.Source;
import schema.grammar.EffectLine;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * End-to-end proof that the variable vocabulary is the single source of truth: an
 * authored condition is compiled to slot-resolved IR using {@code vocabulary.asResolver()},
 * then evaluated over a {@code FactBuffer} the same vocabulary sized and whose slots it
 * names — so compile-time slots and runtime population agree by construction (§3.4).
 */
class ConditionRoundTripTest {

    private final VarVocabulary vocab = BuiltinVars.vocabulary();

    private Ability compile(String condition) {
        EffectRegistry effects = BuiltinEffects.registry();
        SelectorRegistry selectors = BuiltinSelectors.registry();
        Compiler compiler = Compiler.of(effects.specRegistry(), effects.affinityOf(),
                selectors.specRegistry(), effects.defaultSelectorOf(), vocab.asResolver());

        AbilityDef def = new AbilityDef(SourceKind.ENCHANT, "ench/cond", 1, 1, 100.0, 0, 0,
                List.of("ATTACK"), List.of(), condition,
                List.of(EffectLine.parse("DAMAGE:6", Source.of("enchants.yml", 1, 1))),
                null, null, null, null, 0, Source.ofFile("enchants.yml"), 0);

        Diagnostics d = new Diagnostics();
        Snapshot snap = compiler.compile(List.of(def), 1, d);
        assertFalse(d.hasErrors(), () -> d.all().toString());
        Ability a = snap.byStableKey("ench/cond");
        assertNotNull(a.condition());
        return a;
    }

    @Test
    void compiledConditionEvaluatesAgainstAMatchingFactBuffer() {
        Ability a = compile("%victim.health% < 5 && %sneaking%");

        int health = vocab.lookup("victim", "health").orElseThrow().slot();
        int sneaking = vocab.lookup(null, "sneaking").orElseThrow().slot();

        FactBuffer f = vocab.newFactBuffer();
        f.setNumber(health, 3.0);
        f.setFlag(sneaking, true);
        assertTrue(ConditionEvaluator.eval(a.condition(), f).passes()); // 3 < 5 && sneaking

        f.setNumber(health, 10.0); // 10 < 5 is false
        assertFalse(ConditionEvaluator.eval(a.condition(), f).passes());

        f.setNumber(health, 3.0);
        f.setFlag(sneaking, false); // not sneaking
        assertFalse(ConditionEvaluator.eval(a.condition(), f).passes());
    }
}
