package engine.trigger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.Compiler;
import compile.def.AbilityDef;
import compile.model.Ability;
import compile.model.Snapshot;
import compile.model.SourceKind;
import engine.condition.BuiltinVars;
import engine.effect.EffectRegistry;
import engine.effect.kind.BuiltinEffects;
import engine.selector.SelectorRegistry;
import engine.selector.kind.BuiltinSelectors;
import schema.diag.Diagnostics;
import schema.diag.Source;
import schema.grammar.EffectLine;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Trigger names intern against the canonical {@link TriggerRegistry} so a {@code triggerMask} bit means the trigger the runtime routes (§3.7); an unknown trigger is a diagnostic, not a silent ghost. */
class TriggerBridgeTest {

    private final TriggerRegistry triggers = BuiltinTriggers.registry();

    private Compiler compiler() {
        EffectRegistry effects = BuiltinEffects.registry();
        SelectorRegistry selectors = BuiltinSelectors.registry();
        return Compiler.of(effects.specRegistry(), effects.affinityOf(),
                selectors.specRegistry(), effects.defaultSelectorOf(),
                BuiltinVars.vocabulary().asResolver(), triggers.names());
    }

    private static AbilityDef ability(String stableKey, List<String> triggerNames) {
        return new AbilityDef(SourceKind.ENCHANT, stableKey, 1, 1, 100.0, 0, 0,
                triggerNames, List.of(), null,
                List.of(EffectLine.parse("DAMAGE:6", Source.of("enchants.yml", 1, 1))),
                null, null, null, null, 0, Source.ofFile("enchants.yml"), 0);
    }

    @Test
    void contentTriggerNameInternsToTheCanonicalId() {
        Diagnostics d = new Diagnostics();
        Snapshot snap = compiler().compile(List.of(ability("ench/atk", List.of("attack"))), 1, d);

        assertFalse(d.hasErrors());
        Ability a = snap.byStableKey("ench/atk");
        int attackId = triggers.idOf("ATTACK").orElseThrow();
        assertTrue(a.firesOn(attackId)); // lowercase "attack" interned to the canonical ATTACK id
        assertEquals(attackId, snap.interners().triggers().idOf("ATTACK"));
    }

    @Test
    void unknownTriggerIsDiagnosed() {
        Diagnostics d = new Diagnostics();
        compiler().compile(List.of(ability("ench/bad", List.of("flibbertigibbet"))), 1, d);
        assertTrue(d.hasErrors());
        assertEquals("E_UNKNOWN_TRIGGER", d.all().get(0).code());
    }
}
